package feature.report

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import model.ExpenseItem
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.UserBalance
import kotlin.test.Test

/**
 * commonMain 化済みの Content composable を JVM ターゲット上でオフスクリーンレンダリングし、
 * 3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
class PreviewScreenshotGeneratorTest {
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "report")

    @Test
    fun generateReportScreenshots() {
        val summary =
            MonthlyExpenseSummary(
                yearMonth = "2026-07",
                totalAmount = 32000,
                items = listOf(ExpenseItem(name = "電気代", amount = 8000), ExpenseItem(name = "食費", amount = 24000)),
            )
        val report = ExpenseReport(months = listOf(summary))

        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "report_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ReportContent(
                            report = report,
                            selectedYearMonth = "2026-07",
                            selectedSummary = summary,
                            averageAmount = 30000,
                            previousMonthDiff = 2000,
                            isLoading = false,
                            error = null,
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onSelectYearMonth = {},
                            windowSizeClass = pattern.windowSizeClass,
                        )
                    }
                }
            }
        }

        val balances =
            listOf(
                UserBalance(uid = "user1", displayName = "たろう", allocated = 4000, paid = 2000, remaining = 2000),
            )

        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "overpayment_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        OverpaymentContent(
                            balances = balances,
                            period = "2026-07",
                            isLoading = false,
                            onRefresh = {},
                            isCompact = pattern.windowSizeClass == WindowSizeClass.Compact,
                            redemptionForm = RedemptionFormState(selectedYearMonth = "2026-07"),
                            onSelectUser = {},
                            onAmountChange = {},
                            onNoteChange = {},
                            onMonthPrevious = {},
                            onMonthNext = {},
                            onFillRemaining = {},
                            onClear = {},
                            onConfirm = {},
                        )
                    }
                }
            }
        }
    }
}
