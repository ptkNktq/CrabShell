package feature.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import kotlin.test.Test

/**
 * commonMain 化済みの Content composable を JVM ターゲット上でオフスクリーンレンダリングし、
 * 3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
class PreviewScreenshotGeneratorTest {
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "auth")

    @Test
    fun generateAuthScreenshots() {
        standardSizePatterns.forEach { pattern ->
            recorder.save(
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

        standardSizePatterns.forEach { pattern ->
            recorder.save(
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
