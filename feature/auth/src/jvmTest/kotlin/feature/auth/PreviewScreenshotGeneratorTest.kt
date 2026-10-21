package feature.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import model.McpConnection
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

        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "mcp_connect_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                // McpConnectContent も内部で AppTheme + Surface を適用済み
                McpConnectContent(
                    isCompleting = false,
                    errorMessage = null,
                    disabledConnections = DisabledConnectionsState(),
                    onToggleDisabledExpanded = {},
                    onDisabledFilterQueryChange = {},
                    onEnableConnection = {},
                    onContinue = {},
                    onCancel = {},
                )
            }
        }

        // 無効にした連携アプリがある場合の案内（件数・開閉・絞り込み・有効化の状態ごと）
        val disabledStates =
            mapOf(
                "disabled_few" to DisabledConnectionsState(connections = sampleDisabledConnections(2)),
                "disabled_many_collapsed" to DisabledConnectionsState(connections = sampleDisabledConnections(12)),
                "disabled_many_expanded" to DisabledConnectionsState(connections = sampleDisabledConnections(12), isExpanded = true),
                "disabled_many_filtered" to
                    DisabledConnectionsState(connections = sampleDisabledConnections(12), isExpanded = true, filterQuery = "claude"),
                "disabled_enabling" to
                    DisabledConnectionsState(
                        connections = sampleDisabledConnections(3),
                        enablingId = "c1",
                        enabledIds = setOf("c0"),
                    ),
            )
        standardSizePatterns.forEach { pattern ->
            disabledStates.forEach { (label, state) ->
                recorder.save(
                    fileName = "mcp_connect_${label}_${pattern.label}.png",
                    width = pattern.width,
                    height = pattern.height,
                ) {
                    McpConnectContent(
                        isCompleting = false,
                        errorMessage = null,
                        disabledConnections = state,
                        onToggleDisabledExpanded = {},
                        onDisabledFilterQueryChange = {},
                        onEnableConnection = {},
                        onContinue = {},
                        onCancel = {},
                    )
                }
            }
        }

        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "mcp_connect_cancelled_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                McpConnectCancelledContent()
            }
        }
    }

    private fun sampleDisabledConnections(count: Int): List<McpConnection> {
        val names =
            listOf(
                "Claude Code",
                "Cursor",
                "Claude Desktop",
                "VS Code",
                "Windsurf",
                "Zed",
                "claude.example.com",
                "Cline",
                "Goose",
                "Continue",
                "Raycast",
                "LibreChat",
            )
        return List(count) { i ->
            McpConnection(
                id = "c$i",
                clientName = names[i % names.size],
                firstUsedAt = "2026-09-01T00:00:00Z",
                lastUsedAt = "2026-10-01T00:00:00Z",
                // 無効にした日の新しい順
                disabledAt = "2026-10-${(20 - i).toString().padStart(2, '0')}T03:00:00Z",
            )
        }
    }
}
