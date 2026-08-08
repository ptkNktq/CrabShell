package feature.money

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import model.MoneyItem
import model.MonthlyMoney
import model.Share
import model.User
import kotlin.test.Test

/**
 * commonMain 化済みの Content composable を JVM ターゲット上でオフスクリーンレンダリングし、
 * 3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
class PreviewScreenshotGeneratorTest {
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "money")

    @Test
    fun generateMoneyScreenshots() {
        val users =
            listOf(
                User(uid = "user1", email = "user1@example.com", displayName = "たろう"),
                User(uid = "user2", email = "user2@example.com", displayName = "はなこ"),
            )
        val monthlyMoney =
            MonthlyMoney(
                yearMonth = "2026-07",
                items =
                    listOf(
                        MoneyItem(
                            id = "item1",
                            name = "電気代",
                            amount = 8000,
                            dueDate = "2026-07-25",
                            shares = listOf(Share("user1", 4000), Share("user2", 4000)),
                        ),
                        MoneyItem(
                            id = "item2",
                            name = "食費",
                            amount = 5000,
                            shares = listOf(Share("user1", 2500), Share("user2", 2500)),
                        ),
                    ),
            )

        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "money_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MoneyContent(
                            monthlyMoney = monthlyMoney,
                            currentYearMonth = "2026-07",
                            loading = false,
                            saving = false,
                            statusSaving = false,
                            error = null,
                            users = users,
                            editingItem = null,
                            formKey = 0,
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onEditItem = {},
                            onClearForm = {},
                            onDeleteItem = {},
                            onMoveItem = { _, _ -> },
                            onSaveItem = { _, _, _, _, _, _ -> },
                            onUpdateStatus = {},
                            onImportRecurringItems = {},
                            windowSizeClass = pattern.windowSizeClass,
                        )
                    }
                }
            }
        }
    }
}
