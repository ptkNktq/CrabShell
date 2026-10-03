package feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.browser.window
import org.koin.compose.viewmodel.koinViewModel
import org.w3c.dom.url.URLSearchParams

/**
 * MCP 連携画面のパス。WorkOS AuthKit の Login URI（Connect の設定）にこのパスを登録する。
 * AuthKit は `?external_auth_id=...` を付けてここへ転送してくる。
 */
const val MCP_CONNECT_PATH = "/mcp-connect"

@Composable
fun McpConnectScreen(vm: McpConnectViewModel = koinViewModel()) {
    val externalAuthId = remember { URLSearchParams(window.location.search.toJsString()).get("external_auth_id") }

    LaunchedEffect(vm.uiState.redirectUri) {
        vm.uiState.redirectUri?.let { window.location.href = it }
    }

    McpConnectContent(
        isCompleting = vm.uiState.isCompleting,
        errorMessage = vm.uiState.errorMessage,
        onContinue = { vm.onContinue(externalAuthId) },
        // 連携を中断してアプリのトップへ戻る（URL に残った external_auth_id も破棄する）
        onCancel = { window.location.href = "/" },
    )
}
