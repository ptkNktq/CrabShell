package feature.report

import androidx.compose.runtime.Composable
import core.ui.LocalWindowSizeClass
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportScreen(vm: ReportViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current

    ReportContent(
        report = vm.uiState.report,
        selectedYearMonth = vm.uiState.selectedYearMonth,
        selectedSummary = vm.uiState.selectedSummary,
        averageAmount = vm.uiState.averageAmount,
        previousMonthDiff = vm.uiState.previousMonthDiff,
        isLoading = vm.uiState.isLoading,
        error = vm.uiState.error,
        onPreviousMonth = vm::onGoToPreviousMonth,
        onNextMonth = vm::onGoToNextMonth,
        onSelectYearMonth = vm::onSelectYearMonth,
        windowSizeClass = windowSizeClass,
    )
}
