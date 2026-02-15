@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.auth.toJsString
import core.network.MoneyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.MonthlyMoney
import model.PaymentRecord

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
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class PaymentViewModel(
    private val scope: CoroutineScope,
    private val moneyRepository: MoneyRepository,
) {
    var uiState by mutableStateOf(
        PaymentUiState(
            currentMonth = currentMonthJs().toString(),
            monthlyMoney = MonthlyMoney(month = currentMonthJs().toString()),
            currentUid = (AuthStateHolder.state as? AuthState.Authenticated)?.user?.uid ?: "",
        ),
    )
        private set

    init {
        onLoadMonth(uiState.currentMonth)
    }

    fun onLoadMonth(month: String) {
        uiState = uiState.copy(currentMonth = month, isLoading = true, error = null)
        scope.launch {
            try {
                uiState =
                    uiState.copy(
                        monthlyMoney = moneyRepository.getMyMonthlyMoney(month),
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

    fun onRecordPayment(amount: Long) {
        uiState = uiState.copy(isSaving = true)
        scope.launch {
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
