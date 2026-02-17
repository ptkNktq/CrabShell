@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.AuthState
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
external fun currentMonthJs(): JsString

/** 月を offset 分ずらす */
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

/** 現在の日時を UTC の ISO 形式で返す（サーバー送信用） */
@JsFun("() => new Date().toISOString()")
external fun nowIsoJs(): JsString

data class PaymentUiState(
    val monthlyMoney: MonthlyMoney = MonthlyMoney(month = ""),
    val currentMonth: String = "",
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
) : ViewModel() {
    private val authUser = (AuthStateHolder.state as? AuthState.Authenticated)?.user

    var uiState by mutableStateOf(
        PaymentUiState(
            currentMonth = currentMonthJs().toString(),
            monthlyMoney = MonthlyMoney(month = currentMonthJs().toString()),
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
            loadMonth(uiState.currentMonth)
        }
    }

    fun onLoadMonth(month: String) {
        uiState = uiState.copy(currentMonth = month, isLoading = true, error = null)
        viewModelScope.launch { loadMonth(month) }
    }

    fun onSwitchUser(uid: String) {
        uiState = uiState.copy(viewingUid = uid, isLoading = true, error = null)
        viewModelScope.launch { loadMonth(uiState.currentMonth) }
    }

    private suspend fun loadMonth(month: String) {
        try {
            val monthly =
                if (uiState.isViewingOther) {
                    val full = moneyRepository.getMonthlyMoney(month)
                    val uid = uiState.viewingUid
                    val myItems = full.items.filter { item -> item.payments.any { it.uid == uid } }
                    val myRecords = full.paymentRecords.filter { it.uid == uid }
                    full.copy(items = myItems, paymentRecords = myRecords)
                } else {
                    moneyRepository.getMyMonthlyMoney(month)
                }
            uiState = uiState.copy(monthlyMoney = monthly, isLoading = false)
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message, isLoading = false)
        }
    }

    fun onGoToPreviousMonth() {
        onLoadMonth(shiftMonthJs(uiState.currentMonth.toJsString(), -1).toString())
    }

    fun onGoToNextMonth() {
        onLoadMonth(shiftMonthJs(uiState.currentMonth.toJsString(), 1).toString())
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
                        monthlyMoney = moneyRepository.recordPayment(uiState.currentMonth, record),
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}
