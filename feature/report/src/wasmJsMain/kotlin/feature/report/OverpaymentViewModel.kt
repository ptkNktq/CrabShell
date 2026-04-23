@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.toJsString
import core.network.MoneyRepository
import core.network.ReportRepository
import kotlinx.coroutines.launch
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.OverpaymentRedemptionRequest
import model.UserBalance

private const val DEFAULT_REDEMPTION_NOTE = "過払い金から支払い"

data class RedemptionFormState(
    val selectedUid: String = "",
    val selectedYearMonth: String = "",
    val amountText: String = "",
    val noteText: String = DEFAULT_REDEMPTION_NOTE,
    val isSaving: Boolean = false,
    val error: String? = null,
    val monthData: MonthlyMoney? = null,
) {
    val isMonthFrozen: Boolean get() = monthData?.status == MonthlyMoneyStatus.FROZEN
    val canSubmit: Boolean
        get() =
            selectedUid.isNotEmpty() &&
                (amountText.toLongOrNull() ?: 0L) > 0L &&
                !isSaving &&
                !isMonthFrozen
}

data class OverpaymentUiState(
    val balances: List<UserBalance> = emptyList(),
    val period: String = "",
    val isLoading: Boolean = true,
    val redemptionForm: RedemptionFormState = RedemptionFormState(),
)

class OverpaymentViewModel(
    private val reportRepository: ReportRepository,
    private val moneyRepository: MoneyRepository,
) : ViewModel() {
    var uiState by mutableStateOf(
        OverpaymentUiState(
            redemptionForm = RedemptionFormState(selectedYearMonth = currentYearMonthJs().toString()),
        ),
    )
        private set

    init {
        loadBalances()
        loadMonthData()
    }

    fun loadBalances() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            try {
                val summary = reportRepository.getBalanceSummary()
                val period =
                    if (summary.periodStart.isNotEmpty()) {
                        "${summary.periodStart} 〜 ${summary.periodEnd}"
                    } else {
                        ""
                    }
                uiState =
                    uiState.copy(
                        balances = summary.balances,
                        period = period,
                        isLoading = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(balances = emptyList(), period = "", isLoading = false)
            }
        }
    }

    private fun loadMonthData() {
        val yearMonth = uiState.redemptionForm.selectedYearMonth
        viewModelScope.launch {
            try {
                val data = moneyRepository.getMonthlyMoney(yearMonth)
                uiState =
                    uiState.copy(
                        redemptionForm = uiState.redemptionForm.copy(monthData = data),
                    )
            } catch (_: Exception) {
                uiState =
                    uiState.copy(
                        redemptionForm = uiState.redemptionForm.copy(monthData = null),
                    )
            }
        }
    }

    fun onSelectUser(uid: String) {
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(selectedUid = uid, error = null),
            )
    }

    fun onClearForm() {
        val yearMonth = currentYearMonthJs().toString()
        uiState =
            uiState.copy(
                redemptionForm = RedemptionFormState(selectedYearMonth = yearMonth),
            )
        loadMonthData()
    }

    fun onRedemptionNoteChange(text: String) {
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(noteText = text),
            )
    }

    fun onRedemptionAmountChange(text: String) {
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(amountText = text, error = null),
            )
    }

    fun onRedemptionMonthPrevious() {
        val newYearMonth = shiftYearMonthJs(uiState.redemptionForm.selectedYearMonth.toJsString(), -1).toString()
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(selectedYearMonth = newYearMonth, monthData = null),
            )
        loadMonthData()
    }

    fun onRedemptionMonthNext() {
        val newYearMonth = shiftYearMonthJs(uiState.redemptionForm.selectedYearMonth.toJsString(), 1).toString()
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(selectedYearMonth = newYearMonth, monthData = null),
            )
        loadMonthData()
    }

    fun onFillRemainingAmount() {
        val form = uiState.redemptionForm
        val uid = form.selectedUid
        if (uid.isEmpty()) return
        val data = form.monthData ?: return
        val allocated =
            data.items
                .flatMap { it.payments }
                .filter { it.uid == uid }
                .sumOf { it.amount }
        val paid = data.paymentRecords.filter { it.uid == uid }.sumOf { it.amount }
        val remaining = (allocated - paid).coerceAtLeast(0L)
        uiState =
            uiState.copy(
                redemptionForm = form.copy(amountText = remaining.toString()),
            )
    }

    fun onConfirmRedemption() {
        val form = uiState.redemptionForm
        val amount = form.amountText.toLongOrNull() ?: 0L
        if (form.selectedUid.isEmpty()) {
            uiState =
                uiState.copy(
                    redemptionForm = form.copy(error = "ユーザーを選択してください"),
                )
            return
        }
        if (amount <= 0L) {
            uiState =
                uiState.copy(
                    redemptionForm = form.copy(error = "金額を入力してください"),
                )
            return
        }
        if (form.isMonthFrozen) {
            uiState =
                uiState.copy(
                    redemptionForm = form.copy(error = "この月は凍結されています"),
                )
            return
        }

        viewModelScope.launch {
            uiState =
                uiState.copy(
                    redemptionForm = form.copy(isSaving = true, error = null),
                )
            try {
                reportRepository.redeemOverpayment(
                    OverpaymentRedemptionRequest(
                        uid = form.selectedUid,
                        yearMonth = form.selectedYearMonth,
                        amount = amount,
                        note = form.noteText,
                    ),
                )
                onClearForm()
                loadBalances()
            } catch (e: Exception) {
                uiState =
                    uiState.copy(
                        redemptionForm =
                            uiState.redemptionForm.copy(
                                isSaving = false,
                                error = e.message ?: "エラーが発生しました",
                            ),
                    )
            }
        }
    }
}
