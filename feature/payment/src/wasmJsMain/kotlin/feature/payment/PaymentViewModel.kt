@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.AuthStateHolder
import core.auth.toJsString
import core.network.MoneyRepository
import core.network.UserRepository
import kotlinx.coroutines.launch
import model.MonthlyMoney
import model.PaymentRecord
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
external fun currentYearMonthJs(): JsString

/** 年月を offset 月分ずらす */
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

/** 現在の日時を UTC の ISO 形式で返す（サーバー送信用） */
@JsFun("() => new Date().toISOString()")
external fun nowIsoJs(): JsString

data class PaymentUiState(
    val monthlyMoney: MonthlyMoney = MonthlyMoney(yearMonth = ""),
    val currentYearMonth: String = "",
    val currentUid: String = "",
    val viewingUid: String = "",
    val isAdmin: Boolean = false,
    val users: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val isViewingOther: Boolean get() = viewingUid != currentUid
}

class PaymentViewModel(
    private val moneyRepository: MoneyRepository,
    private val userRepository: UserRepository,
    private val authStateHolder: AuthStateHolder,
) : ViewModel() {
    private val authUser = authStateHolder.currentUser

    var uiState by mutableStateOf(
        PaymentUiState(
            currentYearMonth = currentYearMonthJs().toString(),
            monthlyMoney = MonthlyMoney(yearMonth = currentYearMonthJs().toString()),
            currentUid = authUser?.uid ?: "",
            viewingUid = authUser?.uid ?: "",
            isAdmin = authUser?.isAdmin ?: false,
        ),
    )
        private set

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val users =
                if (uiState.isAdmin) {
                    try {
                        userRepository.getUsers()
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            uiState = uiState.copy(users = users)
            loadYearMonth(uiState.currentYearMonth)
        }
    }

    fun onLoadYearMonth(yearMonth: String) {
        uiState = uiState.copy(currentYearMonth = yearMonth, isLoading = true, error = null)
        viewModelScope.launch { loadYearMonth(yearMonth) }
    }

    fun onSwitchUser(uid: String) {
        uiState = uiState.copy(viewingUid = uid, isLoading = true, error = null)
        viewModelScope.launch { loadYearMonth(uiState.currentYearMonth) }
    }

    private suspend fun loadYearMonth(yearMonth: String) {
        try {
            val monthly =
                if (uiState.isViewingOther) {
                    val full = moneyRepository.getMonthlyMoney(yearMonth)
                    val uid = uiState.viewingUid
                    val myItems = full.items.filter { item -> item.payments.any { it.uid == uid } }
                    val myRecords = full.paymentRecords.filter { it.uid == uid }
                    full.copy(items = myItems, paymentRecords = myRecords)
                } else {
                    moneyRepository.getMyMonthlyMoney(yearMonth)
                }
            uiState = uiState.copy(monthlyMoney = monthly, isLoading = false)
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message, isLoading = false)
        }
    }

    fun onGoToPreviousMonth() {
        onLoadYearMonth(shiftYearMonthJs(uiState.currentYearMonth.toJsString(), -1).toString())
    }

    fun onGoToNextMonth() {
        onLoadYearMonth(shiftYearMonthJs(uiState.currentYearMonth.toJsString(), 1).toString())
    }

    fun onRecordPayment(amount: Long) {
        uiState = uiState.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val record =
                    PaymentRecord(
                        uid = uiState.currentUid,
                        amount = amount,
                        paidAt = nowIsoJs().toString(),
                    )
                uiState =
                    uiState.copy(
                        monthlyMoney = moneyRepository.recordPayment(uiState.currentYearMonth, record),
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}
