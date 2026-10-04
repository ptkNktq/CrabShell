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

    LaunchedEffect(vm.uiState.isCancelled) {
        // 再読み込みや戻る操作で連携を続けられないよう、URL に残った external_auth_id を破棄する
        if (vm.uiState.isCancelled) window.history.replaceState(null, "", MCP_CONNECT_PATH)
    }

    if (vm.uiState.isCancelled) {
        McpConnectCancelledContent()
    } else {
        McpConnectContent(
            isCompleting = vm.uiState.isCompleting,
            errorMessage = vm.uiState.errorMessage,
            onContinue = { vm.onContinue(externalAuthId) },
            onCancel = vm::onCancel,
        )
    }
}
