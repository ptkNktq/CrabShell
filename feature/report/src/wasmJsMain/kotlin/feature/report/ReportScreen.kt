package feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import feature.report.components.CategoryBreakdown
import feature.report.components.MonthlyBarChart
import feature.report.components.ReportSummaryCard
import feature.report.components.UserBalanceCard
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.UserBalance
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportScreen(vm: ReportViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current
    val isAdmin = (AuthStateHolder.state as? AuthState.Authenticated)?.user?.isAdmin == true

    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            vm.loadBalances()
        }
    }

    ReportContent(
        report = vm.uiState.report,
        selectedMonth = vm.uiState.selectedMonth,
        selectedSummary = vm.uiState.selectedSummary,
        averageAmount = vm.uiState.averageAmount,
        previousMonthDiff = vm.uiState.previousMonthDiff,
        isLoading = vm.uiState.isLoading,
        error = vm.uiState.error,
        onPreviousMonth = vm::onGoToPreviousMonth,
        onNextMonth = vm::onGoToNextMonth,
        windowSizeClass = windowSizeClass,
        userBalances = if (isAdmin) vm.uiState.userBalances else emptyList(),
        onRefreshBalances = vm::loadBalances,
    )
}

@Composable
internal fun ReportContent(
    report: ExpenseReport,
    selectedMonth: String,
    selectedSummary: MonthlyExpenseSummary?,
    averageAmount: Long,
    previousMonthDiff: Long?,
    isLoading: Boolean,
    error: String?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
    userBalances: List<UserBalance> = emptyList(),
    onRefreshBalances: () -> Unit = {},
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact
    val outerPadding = if (isCompact) 12.dp else 24.dp

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(outerPadding),
    ) {
        Text(
            text = "家計レポート",
            style = if (isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        MonthSelector(
            month = selectedMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
        )
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text("エラー: $error", color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                val spacing = if (isCompact) 12.dp else 16.dp
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item(key = "summary") {
                        if (userBalances.isNotEmpty() && !isCompact) {
                            Row(
                                modifier =
                                    Modifier
                                        .widthIn(max = 900.dp)
                                        .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                            ) {
                                ReportSummaryCard(
                                    currentTotal = selectedSummary?.totalAmount ?: 0L,
                                    averageAmount = averageAmount,
                                    previousMonthDiff = previousMonthDiff,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                UserBalanceCard(
                                    balances = userBalances,
                                    onRefresh = onRefreshBalances,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        } else {
                            ReportSummaryCard(
                                currentTotal = selectedSummary?.totalAmount ?: 0L,
                                averageAmount = averageAmount,
                                previousMonthDiff = previousMonthDiff,
                                modifier = Modifier.widthIn(max = 600.dp),
                            )
                        }
                    }

                    if (userBalances.isNotEmpty() && isCompact) {
                        item(key = "balances") {
                            UserBalanceCard(
                                balances = userBalances,
                                onRefresh = onRefreshBalances,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item(key = "chart") {
                        MonthlyBarChart(
                            months = report.months,
                            selectedMonth = selectedMonth,
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
}

@Composable
private fun MonthSelector(
    month: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val parts = month.split("-")
    val displayText =
        if (parts.size == 2) "${parts[0]}年${parts[1].toInt()}月" else month

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
