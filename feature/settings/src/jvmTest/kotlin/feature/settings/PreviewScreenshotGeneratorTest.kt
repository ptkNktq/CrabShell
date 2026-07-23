package feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * @Preview 本導入の判断材料として、commonMain 化済みの settings 画面を
 * JVM ターゲット上でオフスクリーンレンダリングし、3パターンの画面サイズで
 * PNG 出力する PoC。Android ターゲットが無いため IDE のプレビューパネルは
 * 使えないので、ここでは自動スクショ生成のみを検証する。
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
            SizePattern("compact", 375, 700, WindowSizeClass.Compact),
            SizePattern("medium", 700, 700, WindowSizeClass.Medium),
            SizePattern("expanded", 1000, 700, WindowSizeClass.Expanded),
        )

    private fun saveScreenshot(
        fileName: String,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width = width, height = height) { content() }
        try {
            scene.render()
            val image = scene.render()
            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outputDir, fileName).writeBytes(bytes)
        } finally {
            scene.close()
        }
    }

    // SettingsContent 自体は categoryContent 内で koinViewModel() を呼ぶ実画面へ
    // 委譲しているため、Koin なしでプレビューするにはフェイクの categoryContent を
    // 差し込む必要がある（本 PoC のための最小限の変更を SettingsScreen.kt に追加済み）。
    @Test
    fun generateSettingsContentScreenshots() {
        sizePatterns.forEach { pattern ->
            saveScreenshot(
                fileName = "settings_content_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        SettingsContent(
                            isAdmin = true,
                            windowSizeClass = pattern.windowSizeClass,
                            selectedCategory = null,
                            categoryContent = { category, modifier ->
                                Box(modifier = modifier.fillMaxSize()) {
                                    Text("プレビュー用ダミー: ${category.title}")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // CreditsCard は koinViewModel に依存しない純粋な stateless コンポーネント。
    // Screen 層と違い、サンプルデータを渡すだけでそのままプレビュー可能。
    @Test
    fun generateCreditsCardScreenshots() {
        sizePatterns.forEach { pattern ->
            saveScreenshot(
                fileName = "credits_card_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(contentAlignment = Alignment.TopStart) {
                            val cardWidth =
                                if (pattern.windowSizeClass == WindowSizeClass.Compact) {
                                    (pattern.width - 32).dp
                                } else {
                                    480.dp
                                }
                            CreditsCard(
                                isLoading = false,
                                libraries = emptyList(),
                                error = null,
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}
