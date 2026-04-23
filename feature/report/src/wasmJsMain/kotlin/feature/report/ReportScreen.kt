package feature.report

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import feature.report.components.CategoryBreakdown
import feature.report.components.MonthlyBarChart
import feature.report.components.ReportSummaryCard
import model.ExpenseReport
import model.MonthlyExpenseSummary
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

@Composable
internal fun ReportContent(
    report: ExpenseReport,
    selectedYearMonth: String,
    selectedSummary: MonthlyExpenseSummary?,
    averageAmount: Long,
    previousMonthDiff: Long?,
    isLoading: Boolean,
    error: String?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectYearMonth: (String) -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact
    val outerPadding = if (isCompact) 12.dp else 24.dp

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onPreviousMonth()
                                true
                            }
                            Key.DirectionRight -> {
                                onNextMonth()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
    ) {
        Text(
            text = "家計レポート",
            style = if (isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        MonthSelector(
            yearMonth = selectedYearMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
        )
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        ReportMainContent(
            report = report,
            selectedYearMonth = selectedYearMonth,
            selectedSummary = selectedSummary,
            averageAmount = averageAmount,
            previousMonthDiff = previousMonthDiff,
            isLoading = isLoading,
            error = error,
            onSelectYearMonth = onSelectYearMonth,
            isCompact = isCompact,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportMainContent(
    report: ExpenseReport,
    selectedYearMonth: String,
    selectedSummary: MonthlyExpenseSummary?,
    averageAmount: Long,
    previousMonthDiff: Long?,
    isLoading: Boolean,
    error: String?,
    onSelectYearMonth: (String) -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Box(modifier = modifier.fillMaxWidth()) {
                Text("エラー: $error", color = MaterialTheme.colorScheme.error)
            }
        }

        else -> {
            val spacing = if (isCompact) 12.dp else 16.dp
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "summary") {
                    ReportSummaryCard(
                        currentTotal = selectedSummary?.totalAmount ?: 0L,
                        averageAmount = averageAmount,
                        previousMonthDiff = previousMonthDiff,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }

                item(key = "chart") {
                    MonthlyBarChart(
                        months = report.months,
                        selectedYearMonth = selectedYearMonth,
                        onYearMonthClick = onSelectYearMonth,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }

                item(key = "breakdown") {
                    CategoryBreakdown(
                        items = selectedSummary?.items ?: emptyList(),
                        totalAmount = selectedSummary?.totalAmount ?: 0L,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    yearMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val parts = yearMonth.split("-")
    val displayText =
        if (parts.size == 2) "${parts[0]}年${parts[1].toInt()}月" else yearMonth

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前月")
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "翌月")
        }
    }
}
