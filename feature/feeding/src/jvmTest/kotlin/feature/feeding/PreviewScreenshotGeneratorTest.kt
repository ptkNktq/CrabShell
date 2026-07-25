package feature.feeding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import model.Feeding
import model.FeedingLog
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
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "feeding")

    @Test
    fun generateFeedingScreenshots() {
        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "feeding_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        FeedingContent(
                            petName = "モカ",
                            selectedDate = "2026-07-25",
                            today = "2026-07-25",
                            loading = false,
                            error = null,
                            log =
                                FeedingLog(
                                    date = "2026-07-25",
                                    feedings = mapOf(MealTime.MORNING to Feeding(done = true, timestamp = "2026-07-25T00:00:00Z")),
                                ),
                            mealOrder = MealTime.entries,
                            noteDraft = "",
                            editingMealTime = null,
                            onDateSelected = {},
                            onPreviousDay = {},
                            onNextDay = {},
                            onFeed = {},
                            onNoteChange = {},
                            onSaveNote = {},
                            onStartEditTimestamp = {},
                            onCancelEditTimestamp = {},
                            onSaveTimestamp = { _, _, _ -> },
                            windowSizeClass = pattern.windowSizeClass,
                        )
                    }
                }
            }
        }
    }
}
