@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.toJsString
import core.network.MoneyRepository
import core.network.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import model.MoneyItem
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.Share
import model.User
import model.toSaveRequest

/** 現在の年月を "YYYY-MM" 形式で返す */
@JsFun(
    """() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    return y + '-' + m;
}""",
)
external fun currentYearMonthJs(): JsString

/** 年月を offset 月分ずらす (例: "2026-02", 1 → "2026-03") */
@JsFun(
    """(yearMonthStr, offset) => {
    const [y, m] = yearMonthStr.split('-').map(Number);
    const d = new Date(y, m - 1 + offset, 1);
    const ny = d.getFullYear();
    const nm = String(d.getMonth() + 1).padStart(2, '0');
    return ny + '-' + nm;
}""",
)
external fun shiftYearMonthJs(
    yearMonthStr: JsString,
    offset: Int,
): JsString

/** crypto.randomUUID() で UUID を生成 */
@JsFun("() => crypto.randomUUID()")
external fun randomUUID(): JsString

data class MoneyUiState(
    val monthlyMoney: MonthlyMoney = MonthlyMoney(yearMonth = ""),
    val currentYearMonth: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isStatusSaving: Boolean = false,
    val error: String? = null,
    val users: List<User> = emptyList(),
    val editingItem: MoneyItem? = null,
    val formKey: Int = 0,
)

class MoneyViewModel(
    private val moneyRepository: MoneyRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    var uiState by mutableStateOf(
        MoneyUiState(
            currentYearMonth = currentYearMonthJs().toString(),
            monthlyMoney = MonthlyMoney(yearMonth = currentYearMonthJs().toString()),
        ),
    )
        private set

    init {
        loadInitialData()
    }

    private var loadJob: Job? = null

    /** 初回読み込み: ユーザー一覧と月次データを1つの coroutine で取得し、state を一度に更新 */
    private fun loadInitialData() {
        viewModelScope.launch {
            val users =
                try {
                    userRepository.getUsers()
                } catch (_: Exception) {
                    emptyList()
                }
            val yearMonth = uiState.currentYearMonth
            try {
                val monthly = moneyRepository.getMonthlyMoney(yearMonth)
                // 初回読み込み中に月送りされた場合は、古い月次データで上書きしない
                if (uiState.currentYearMonth == yearMonth) {
                    uiState = uiState.copy(users = users, monthlyMoney = monthly, isLoading = false)
                } else {
                    uiState = uiState.copy(users = users)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(users = users, error = e.message, isLoading = false)
            }
        }
    }

    /** 月送り連打時は前のリクエストをキャンセルし、最後の操作の結果のみ反映する */
    fun onLoadYearMonth(yearMonth: String) {
        uiState = uiState.copy(currentYearMonth = yearMonth, isLoading = true, error = null)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                try {
                    uiState =
                        uiState.copy(
                            monthlyMoney = moneyRepository.getMonthlyMoney(yearMonth),
                            isLoading = false,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    uiState = uiState.copy(error = e.message, isLoading = false)
                }
            }
    }

    fun onGoToPreviousMonth() {
        onLoadYearMonth(shiftYearMonthJs(uiState.currentYearMonth.toJsString(), -1).toString())
    }

    fun onGoToNextMonth() {
        onLoadYearMonth(shiftYearMonthJs(uiState.currentYearMonth.toJsString(), 1).toString())
    }

    fun onUpdateStatus(status: MonthlyMoneyStatus) {
        if (uiState.monthlyMoney.status == status || uiState.isStatusSaving) return
        uiState = uiState.copy(isStatusSaving = true)
        viewModelScope.launch {
            try {
                val updated = moneyRepository.updateStatus(uiState.currentYearMonth, status)
                uiState = uiState.copy(monthlyMoney = updated)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isStatusSaving = false)
            }
        }
    }

    fun onEditItem(item: MoneyItem) {
        uiState = uiState.copy(editingItem = item)
    }

    fun onClearForm() {
        uiState = uiState.copy(editingItem = null, formKey = uiState.formKey + 1)
    }

    fun onSaveItem(
        name: String,
        amount: Long,
        note: String,
        dueDate: String?,
        shares: List<Share>,
        tags: List<String>,
    ) {
        val existing = uiState.editingItem

        val newItem =
            if (existing != null) {
                existing.copy(name = name, amount = amount, note = note, dueDate = dueDate, shares = shares, tags = tags)
            } else {
                MoneyItem(
                    id = randomUUID().toString(),
                    name = name,
                    amount = amount,
                    note = note,
                    dueDate = dueDate,
                    shares = shares,
                    tags = tags,
                )
            }

        val updatedItems =
            if (existing != null) {
                uiState.monthlyMoney.items.map { if (it.id == existing.id) newItem else it }
            } else {
                uiState.monthlyMoney.items + newItem
            }

        val isNew = existing == null
        persistAndThen(uiState.monthlyMoney.copy(items = updatedItems)) {
            if (isNew) onClearForm()
        }
    }

    fun onDeleteItem(item: MoneyItem) {
        val updatedItems = uiState.monthlyMoney.items.filter { it.id != item.id }
        if (uiState.editingItem?.id == item.id) onClearForm()
        persistAndThen(uiState.monthlyMoney.copy(items = updatedItems)) {}
    }

    fun onImportRecurringItems() {
        uiState = uiState.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val updated = moneyRepository.importRecurringItems(uiState.currentYearMonth)
                uiState = uiState.copy(monthlyMoney = updated)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }

    /** 項目を前月または次月に移動する（一時機能） */
    fun onMoveItem(
        item: MoneyItem,
        offset: Int,
    ) {
        val targetYearMonth = shiftYearMonthJs(uiState.currentYearMonth.toJsString(), offset).toString()
        uiState = uiState.copy(isSaving = true)
        viewModelScope.launch {
            try {
                // 移動先の月データを取得して項目を追加
                val targetData = moneyRepository.getMonthlyMoney(targetYearMonth)
                val updatedTarget = targetData.copy(items = targetData.items + item)
                moneyRepository.saveMonthlyMoney(targetYearMonth, updatedTarget.toSaveRequest())
                // 現在の月から項目を削除
                val updatedCurrent =
                    uiState.monthlyMoney.copy(
                        items = uiState.monthlyMoney.items.filter { it.id != item.id },
                    )
                moneyRepository.saveMonthlyMoney(uiState.currentYearMonth, updatedCurrent.toSaveRequest())
                uiState = uiState.copy(monthlyMoney = updatedCurrent)
                if (uiState.editingItem?.id == item.id) onClearForm()
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }

    private fun persistAndThen(
        data: MonthlyMoney,
        onSuccess: () -> Unit,
    ) {
        uiState = uiState.copy(isSaving = true)
        viewModelScope.launch {
            try {
                moneyRepository.saveMonthlyMoney(uiState.currentYearMonth, data.toSaveRequest())
                uiState = uiState.copy(monthlyMoney = data)
                onSuccess()
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}
