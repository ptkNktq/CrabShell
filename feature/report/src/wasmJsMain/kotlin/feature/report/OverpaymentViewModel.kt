@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.toJsString
import core.network.ReportRepository
import kotlinx.coroutines.launch
import model.OverpaymentRedemptionRequest
import model.UserBalance

data class RedemptionFormState(
    val selectedUid: String = "",
    val selectedMonth: String = "",
    val amountText: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val amount: Long get() = amountText.toLongOrNull() ?: 0L
}

data class OverpaymentUiState(
    val balances: List<UserBalance> = emptyList(),
    val period: String = "",
    val isLoading: Boolean = true,
    val redemptionForm: RedemptionFormState = RedemptionFormState(),
)

class OverpaymentViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {
    var uiState by mutableStateOf(
        OverpaymentUiState(
            redemptionForm = RedemptionFormState(selectedMonth = currentMonthJs().toString()),
        ),
    )
        private set

    init {
        loadBalances()
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

    fun onSelectUser(uid: String) {
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(selectedUid = uid, error = null),
            )
    }

    fun onClearForm() {
        uiState =
            uiState.copy(
                redemptionForm = RedemptionFormState(selectedMonth = currentMonthJs().toString()),
            )
    }

    fun onRedemptionAmountChange(text: String) {
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(amountText = text, error = null),
            )
    }

    fun onRedemptionMonthPrevious() {
        val newMonth = shiftMonthJs(uiState.redemptionForm.selectedMonth.toJsString(), -1).toString()
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(selectedMonth = newMonth),
            )
    }

    fun onRedemptionMonthNext() {
        val newMonth = shiftMonthJs(uiState.redemptionForm.selectedMonth.toJsString(), 1).toString()
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(selectedMonth = newMonth),
            )
    }

    fun onFillRemainingAmount() {
        val uid = uiState.redemptionForm.selectedUid
        val balance = uiState.balances.find { it.uid == uid } ?: return
        uiState =
            uiState.copy(
                redemptionForm = uiState.redemptionForm.copy(amountText = balance.remaining.toString()),
            )
    }

    fun onConfirmRedemption() {
        val form = uiState.redemptionForm
        if (form.selectedUid.isEmpty()) {
            uiState =
                uiState.copy(
                    redemptionForm = form.copy(error = "ユーザーを選択してください"),
                )
            return
        }
        if (form.amount <= 0L) {
            uiState =
                uiState.copy(
                    redemptionForm = form.copy(error = "金額を入力してください"),
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
                        month = form.selectedMonth,
                        amount = form.amount,
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
