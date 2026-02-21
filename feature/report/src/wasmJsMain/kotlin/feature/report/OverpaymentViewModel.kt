package feature.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.ReportRepository
import kotlinx.coroutines.launch
import model.UserBalance

data class OverpaymentUiState(
    val balances: List<UserBalance> = emptyList(),
    val period: String = "",
    val isLoading: Boolean = true,
)

class OverpaymentViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {
    var uiState by mutableStateOf(OverpaymentUiState())
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
                    OverpaymentUiState(
                        balances = summary.balances,
                        period = period,
                        isLoading = false,
                    )
            } catch (e: Exception) {
                uiState = OverpaymentUiState(isLoading = false)
            }
        }
    }
}
