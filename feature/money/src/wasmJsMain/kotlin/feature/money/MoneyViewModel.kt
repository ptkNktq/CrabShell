@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.toJsString
import core.network.MoneyRepository
import core.network.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.User

/** 現在の年月を "YYYY-MM" 形式で返す */
@JsFun(
    """() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    return y + '-' + m;
}""",
)
external fun currentMonthJs(): JsString

/** 月を offset 分ずらす (例: "2026-02", 1 → "2026-03") */
@JsFun(
    """(monthStr, offset) => {
    const [y, m] = monthStr.split('-').map(Number);
    const d = new Date(y, m - 1 + offset, 1);
    const ny = d.getFullYear();
    const nm = String(d.getMonth() + 1).padStart(2, '0');
    return ny + '-' + nm;
}""",
)
external fun shiftMonthJs(
    monthStr: JsString,
    offset: Int,
): JsString

/** crypto.randomUUID() で UUID を生成 */
@JsFun("() => crypto.randomUUID()")
external fun randomUUID(): JsString

data class MoneyUiState(
    val monthlyMoney: MonthlyMoney = MonthlyMoney(month = ""),
    val currentMonth: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val users: List<User> = emptyList(),
    val editingItem: MoneyItem? = null,
    val formKey: Int = 0,
)

class MoneyViewModel(
    private val scope: CoroutineScope,
    private val moneyRepository: MoneyRepository,
    private val userRepository: UserRepository,
) {
    var uiState by mutableStateOf(
        MoneyUiState(
            currentMonth = currentMonthJs().toString(),
            monthlyMoney = MonthlyMoney(month = currentMonthJs().toString()),
        ),
    )
        private set

    init {
        loadUsers()
        onLoadMonth(uiState.currentMonth)
    }

    private fun loadUsers() {
        scope.launch {
            try {
                uiState = uiState.copy(users = userRepository.getUsers())
            } catch (_: Exception) {
                // ユーザー一覧取得失敗は無視
            }
        }
    }

    fun onLoadMonth(month: String) {
        uiState = uiState.copy(currentMonth = month, isLoading = true, error = null)
        scope.launch {
            try {
                uiState =
                    uiState.copy(
                        monthlyMoney = moneyRepository.getMonthlyMoney(month),
                        isLoading = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun onGoToPreviousMonth() {
        onLoadMonth(shiftMonthJs(uiState.currentMonth.toJsString(), -1).toString())
    }

    fun onGoToNextMonth() {
        onLoadMonth(shiftMonthJs(uiState.currentMonth.toJsString(), 1).toString())
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
        payments: List<Payment>,
        recurring: Boolean,
    ) {
        val existing = uiState.editingItem

        val newItem =
            if (existing != null) {
                existing.copy(name = name, amount = amount, note = note, payments = payments, recurring = recurring)
            } else {
                MoneyItem(
                    id = randomUUID().toString(),
                    name = name,
                    amount = amount,
                    note = note,
                    payments = payments,
                    recurring = recurring,
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

    private fun persistAndThen(
        data: MonthlyMoney,
        onSuccess: () -> Unit,
    ) {
        uiState = uiState.copy(isSaving = true)
        scope.launch {
            try {
                moneyRepository.saveMonthlyMoney(data)
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
