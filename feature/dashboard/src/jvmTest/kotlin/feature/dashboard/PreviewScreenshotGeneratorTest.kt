package feature.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import core.ui.theme.AppTheme
import model.FeedingLog
import model.GarbageType
import model.MealTime
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
    // previewScreenshotTest タスク経由の実行時は全モジュール共通の集約先（プロジェクトルート build/preview-screenshots/）
    // へ出力する。System property 未設定（IDE から直接実行等）の場合はこのモジュール配下にフォールバックする。
    private val outputDir =
        File(System.getProperty("previewScreenshot.outputDir") ?: "build/preview-screenshots").apply { mkdirs() }

    private data class SizePattern(
        val label: String,
        val width: Int,
        val height: Int,
        val windowSizeClass: core.ui.WindowSizeClass,
    )

    private val sizePatterns =
        listOf(
            SizePattern("compact", 375, 800, core.ui.WindowSizeClass.Compact),
            SizePattern("medium", 700, 800, core.ui.WindowSizeClass.Medium),
            SizePattern("expanded", 1000, 800, core.ui.WindowSizeClass.Expanded),
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
    fun generateDashboardScreenshots() {
        sizePatterns.forEach { pattern ->
            saveScreenshot(
                fileName = "dashboard_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        DashboardContent(
                            feedingLoading = false,
                            feedingError = null,
                            feedingActionError = null,
                            feedingLog = FeedingLog(date = "2026-07-25"),
                            petName = "モカ",
                            mealOrder = MealTime.entries,
                            todayGarbageTypes = listOf(GarbageType.BURNABLE, GarbageType.RECYCLABLE),
                            garbageUpdateLabel = "毎日 5:00 更新",
                            currentTime = "12:34",
                            currentYear = "2026年",
                            dateWithDay = "7月25日(土)",
                            onFeedClick = {},
                            onRefreshFeeding = {},
                            windowSizeClass = pattern.windowSizeClass,
                        )
                    }
                }
            }
        }
    }
}
