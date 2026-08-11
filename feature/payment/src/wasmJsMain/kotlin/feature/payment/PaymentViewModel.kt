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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import model.MonthlyMoney
import model.PayRequest
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

data class PaymentUiState(
    val monthlyMoney: MonthlyMoney = MonthlyMoney(yearMonth = ""),
    val currentYearMonth: String = "",
    val currentUid: String = "",
    val viewingUid: String = "",
    val isAdmin: Boolean = false,
    val users: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val deletingPaymentId: String? = null,
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

    private var loadJob: Job? = null

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
            startLoadYearMonth(uiState.currentYearMonth)
        }
    }

    fun onLoadYearMonth(yearMonth: String) {
        uiState = uiState.copy(currentYearMonth = yearMonth, isLoading = true, error = null)
        startLoadYearMonth(yearMonth)
    }

    fun onSwitchUser(uid: String) {
        uiState = uiState.copy(viewingUid = uid, isLoading = true, error = null)
        startLoadYearMonth(uiState.currentYearMonth)
    }

    /** 月送り・ユーザー切替の連打時は前のリクエストをキャンセルし、最後の操作の結果のみ反映する */
    private fun startLoadYearMonth(yearMonth: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadYearMonth(yearMonth) }
    }

    private suspend fun loadYearMonth(yearMonth: String) {
        try {
            val monthly =
                if (uiState.isViewingOther) {
                    val full = moneyRepository.getMonthlyMoney(yearMonth)
                    val uid = uiState.viewingUid
                    val myItems = full.items.filter { item -> item.shares.any { it.uid == uid } }
                    val myPayments = full.payments.filter { it.uid == uid }
                    full.copy(items = myItems, payments = myPayments)
                } else {
                    moneyRepository.getMyMonthlyMoney(yearMonth)
                }
            uiState = uiState.copy(monthlyMoney = monthly, isLoading = false)
        } catch (e: CancellationException) {
            throw e
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
                val request = PayRequest(amount = amount)
                uiState =
                    uiState.copy(
                        monthlyMoney = moneyRepository.recordPayment(uiState.currentYearMonth, request),
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }

    fun onDeletePayment(paymentId: String) {
        uiState = uiState.copy(deletingPaymentId = paymentId)
        viewModelScope.launch {
            try {
                // サーバーは filterForUser 済みのデータを返す（onRecordPayment と同じパターン）。
                // 他ユーザー閲覧中には削除ボタン自体が非表示なので isViewingOther の再フィルタは不要。
                uiState =
                    uiState.copy(
                        monthlyMoney = moneyRepository.deletePayment(uiState.currentYearMonth, paymentId),
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(deletingPaymentId = null)
            }
        }
    }
}
