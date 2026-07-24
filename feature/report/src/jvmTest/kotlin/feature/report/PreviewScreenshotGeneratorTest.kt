package feature.report

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import model.ExpenseItem
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.UserBalance
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * @Preview 本導入の判断材料として、commonMain 化済みの Content composable を
 * JVM ターゲット上でオフスクリーンレンダリングし、3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
@OptIn(ExperimentalComposeUiApi::class)
class PreviewScreenshotGeneratorTest {
    private val outputDir =
        File("build/preview-screenshots").apply { mkdirs() }

    private data class SizePattern(
        val label: String,
        val width: Int,
        val height: Int,
        val windowSizeClass: WindowSizeClass,
    )

    private val sizePatterns =
        listOf(
            SizePattern("compact", 375, 800, WindowSizeClass.Compact),
            SizePattern("medium", 700, 800, WindowSizeClass.Medium),
            SizePattern("expanded", 1000, 800, WindowSizeClass.Expanded),
        )

    private fun saveScreenshot(
        fileName: String,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width = width, height = height) { content() }
        try {
            // 1回目の render() で LaunchedEffect 等の副作用・アニメーションの初期状態を確定させ、
            // 2回目の render() で確定した見た目を取得する（ImageComposeScene の既知のウォームアップパターン）。
            scene.render()
            val image = scene.render()
            val bytes = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encode failed" }.bytes
            File(outputDir, fileName).writeBytes(bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun generateReportScreenshots() {
        val summary =
            MonthlyExpenseSummary(
                yearMonth = "2026-07",
                totalAmount = 32000,
                items = listOf(ExpenseItem(name = "電気代", amount = 8000), ExpenseItem(name = "食費", amount = 24000)),
            )
        val report = ExpenseReport(months = listOf(summary))

        sizePatterns.forEach { pattern ->
            saveScreenshot(
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

        sizePatterns.forEach { pattern ->
            saveScreenshot(
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
