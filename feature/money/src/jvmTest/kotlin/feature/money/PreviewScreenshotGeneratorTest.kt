package feature.money

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import model.MoneyItem
import model.MonthlyMoney
import model.Share
import model.User
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
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val moduleName = System.getProperty("previewScreenshot.module") ?: "money"

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
            recordManifestEntry(fileName)
        } finally {
            scene.close()
        }
    }

    /**
     * 画面横断のプレビュー一覧（index.html）向けに、ファイル名からタグを機械的に導出して
     * manifest.tsv に1行追記する。screen はファイル名からサイズラベルとモジュール名を
     * 取り除いた残りの部分（例: "settings_account_compact.png" → screen="account"）。
     */
    private fun recordManifestEntry(fileName: String) {
        val base = fileName.removeSuffix(".png")
        val size = base.substringAfterLast('_')
        val screen = base.substringBeforeLast('_').removePrefix("${moduleName}_")
        File(outputDir, "manifest.tsv").appendText("$fileName\t$moduleName\t$screen\t$size\n")
    }

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
                            shares = listOf(Share("user1", 4000), Share("user2", 4000)),
                        ),
                    ),
            )

        sizePatterns.forEach { pattern ->
            saveScreenshot(
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
                            onSaveItem = { _, _, _, _, _ -> },
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
