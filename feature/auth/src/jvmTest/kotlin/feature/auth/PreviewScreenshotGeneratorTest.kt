package feature.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import core.ui.theme.AppTheme
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
    private val moduleName = System.getProperty("previewScreenshot.module") ?: "auth"

    private val outputDir =
        File("build/preview-screenshots").apply { mkdirs() }

    private data class SizePattern(
        val label: String,
        val width: Int,
        val height: Int,
    )

    private val sizePatterns =
        listOf(
            SizePattern("compact", 375, 800),
            SizePattern("medium", 700, 800),
            SizePattern("expanded", 1000, 800),
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
    fun generateAuthScreenshots() {
        sizePatterns.forEach { pattern ->
            saveScreenshot(
                fileName = "login_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        LoginContent(
                            email = "",
                            password = "",
                            passwordVisible = false,
                            errorMessage = null,
                            isLoading = false,
                            loginMode = LoginMode.PASSKEY,
                            isWebAuthnSupported = true,
                            onEmailChanged = {},
                            onPasswordChanged = {},
                            onTogglePasswordVisibility = {},
                            onSignIn = {},
                            onPasskeySignIn = {},
                            onSwitchToPasskey = {},
                            onSwitchToEmailPassword = {},
                        )
                    }
                }
            }
        }

        sizePatterns.forEach { pattern ->
            saveScreenshot(
                fileName = "passkey_setup_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                // PasskeySetupContent は内部で AppTheme + Surface を自前で適用済みのため、そのまま呼び出す
                PasskeySetupContent(
                    isLoading = false,
                    isRegistering = false,
                    errorMessage = null,
                    onRegister = {},
                    onSkip = {},
                )
            }
        }
    }
}
