package core.previewscreenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import core.ui.WindowSizeClass
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

data class SizePattern(
    val label: String,
    val width: Int,
    val height: Int,
    val windowSizeClass: WindowSizeClass,
)

val standardSizePatterns =
    listOf(
        SizePattern("compact", 375, 800, WindowSizeClass.Compact),
        SizePattern("medium", 700, 800, WindowSizeClass.Medium),
        SizePattern("expanded", 1000, 800, WindowSizeClass.Expanded),
    )

/**
 * 各 feature モジュール/app モジュールの previewScreenshotTest から共通で使う、
 * PNG 保存と manifest.tsv 記録の処理。moduleName にはテスト側で
 * `previewScreenshot.module` システムプロパティから受け取った値を渡す。
 */
@OptIn(ExperimentalComposeUiApi::class)
class PreviewScreenshotRecorder(
    private val moduleName: String,
) {
    private val outputDir = File("build/preview-screenshots").apply { mkdirs() }

    fun save(
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
}
