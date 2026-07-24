package app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.components.NavigationContent
import app.components.Sidebar
import core.ui.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Sidebar は app.Screen に依存する app 固有のナビゲーションコンポーネントで、循環依存になるため
 * feature モジュールの Content からは参照できない。「画面単位」のスクリーンショット対象からは
 * 外れるが、ナビゲーションの見た目を確認する価値があるため例外的にここで生成する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
@OptIn(ExperimentalComposeUiApi::class)
class PreviewScreenshotGeneratorTest {
    private val moduleName = System.getProperty("previewScreenshot.module") ?: "app"

    private val outputDir =
        File("build/preview-screenshots").apply { mkdirs() }

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
     * 取り除いた残りの部分（例: "sidebar_collapsed.png" → screen="sidebar"）。
     */
    private fun recordManifestEntry(fileName: String) {
        val base = fileName.removeSuffix(".png")
        val size = base.substringAfterLast('_')
        val screen = base.substringBeforeLast('_').removePrefix("${moduleName}_")
        File(outputDir, "manifest.tsv").appendText("$fileName\t$moduleName\t$screen\t$size\n")
    }

    @Test
    fun generateSidebarScreenshots() {
        // 折りたたみ状態（デフォルト）: 実際の Sidebar をそのまま描画する
        saveScreenshot("sidebar_collapsed.png", width = 100, height = 700) {
            AppTheme {
                Sidebar(
                    currentScreen = Screen.Dashboard,
                    onNavigate = {},
                    onSignOut = {},
                    version = "abc1234",
                    isAdmin = true,
                )
            }
        }

        // 展開状態: expanded は Sidebar 内部の remember state で、クリック操作をシミュレートしないと
        // 到達できないため、Sidebar と同じ構造を NavigationContent(expanded = true) で組み立てる
        saveScreenshot("sidebar_expanded.png", width = 260, height = 700) {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(240.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp)) {
                        Spacer(modifier = Modifier.height(56.dp))
                        NavigationContent(
                            currentScreen = Screen.Dashboard,
                            onNavigate = {},
                            onSignOut = {},
                            version = "abc1234",
                            isAdmin = true,
                            expanded = true,
                        )
                    }
                }
            }
        }
    }
}
