package feature.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import model.FeedingLog
import model.GarbageType
import model.MealTime
import kotlin.test.Test

/**
 * commonMain 化済みの Content composable を JVM ターゲット上でオフスクリーンレンダリングし、
 * 3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
class PreviewScreenshotGeneratorTest {
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "dashboard")

    @Test
    fun generateDashboardScreenshots() {
        standardSizePatterns.forEach { pattern ->
            recorder.save(
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
