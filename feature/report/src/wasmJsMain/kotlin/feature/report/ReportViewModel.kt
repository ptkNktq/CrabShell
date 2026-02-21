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
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.UserBalance

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

data class ReportUiState(
    val report: ExpenseReport = ExpenseReport(),
    val selectedMonth: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val userBalances: List<UserBalance> = emptyList(),
    val balancePeriod: String = "",
    val isLoadingBalances: Boolean = false,
) {
    val selectedSummary: MonthlyExpenseSummary?
        get() = report.months.find { it.month == selectedMonth }

    val averageAmount: Long
        get() {
            val nonEmpty = report.months.filter { it.totalAmount > 0 }
            return if (nonEmpty.isEmpty()) 0L else nonEmpty.sumOf { it.totalAmount } / nonEmpty.size
        }

    val previousMonthDiff: Long?
        get() {
            val current = selectedSummary ?: return null
            val prevMonth = shiftMonthJs(selectedMonth.toJsString(), -1).toString()
            val prev = report.months.find { it.month == prevMonth } ?: return null
            return current.totalAmount - prev.totalAmount
        }
}

class ReportViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {
    var uiState by mutableStateOf(
        ReportUiState(selectedMonth = currentMonthJs().toString()),
    )
        private set

    init {
        loadReport(uiState.selectedMonth)
    }

    private fun loadReport(center: String) {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, error = null)
                val report = reportRepository.getExpenseReport(center)
                uiState = uiState.copy(report = report, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun loadBalances() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingBalances = true)
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
                        userBalances = summary.balances,
                        balancePeriod = period,
                        isLoadingBalances = false,
                    )
            } catch (_: Exception) {
                uiState = uiState.copy(userBalances = emptyList(), balancePeriod = "", isLoadingBalances = false)
            }
        }
    }

    fun onGoToPreviousMonth() {
        val newMonth = shiftMonthJs(uiState.selectedMonth.toJsString(), -1).toString()
        uiState = uiState.copy(selectedMonth = newMonth)
        loadReport(newMonth)
    }

    fun onGoToNextMonth() {
        val newMonth = shiftMonthJs(uiState.selectedMonth.toJsString(), 1).toString()
        uiState = uiState.copy(selectedMonth = newMonth)
        loadReport(newMonth)
    }
}
