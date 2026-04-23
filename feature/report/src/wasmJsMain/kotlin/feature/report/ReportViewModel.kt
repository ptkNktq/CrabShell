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

data class ReportUiState(
    val report: ExpenseReport = ExpenseReport(),
    val selectedYearMonth: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val selectedSummary: MonthlyExpenseSummary?
        get() = report.months.find { it.yearMonth == selectedYearMonth }

    val averageAmount: Long
        get() {
            val nonEmpty = report.months.filter { it.totalAmount > 0 }
            return if (nonEmpty.isEmpty()) 0L else nonEmpty.sumOf { it.totalAmount } / nonEmpty.size
        }

    val previousMonthDiff: Long?
        get() {
            val current = selectedSummary ?: return null
            val prevYearMonth = shiftYearMonthJs(selectedYearMonth.toJsString(), -1).toString()
            val prev = report.months.find { it.yearMonth == prevYearMonth } ?: return null
            return current.totalAmount - prev.totalAmount
        }
}

class ReportViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {
    var uiState by mutableStateOf(
        ReportUiState(selectedYearMonth = currentYearMonthJs().toString()),
    )
        private set

    init {
        loadReport(uiState.selectedYearMonth)
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

    fun onGoToPreviousMonth() {
        val newYearMonth = shiftYearMonthJs(uiState.selectedYearMonth.toJsString(), -1).toString()
        uiState = uiState.copy(selectedYearMonth = newYearMonth)
        loadReport(newYearMonth)
    }

    fun onGoToNextMonth() {
        val newYearMonth = shiftYearMonthJs(uiState.selectedYearMonth.toJsString(), 1).toString()
        uiState = uiState.copy(selectedYearMonth = newYearMonth)
        loadReport(newYearMonth)
    }

    fun onSelectYearMonth(yearMonth: String) {
        if (yearMonth != uiState.selectedYearMonth) {
            uiState = uiState.copy(selectedYearMonth = yearMonth)
            loadReport(yearMonth)
        }
    }
}
