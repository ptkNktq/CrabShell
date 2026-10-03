package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.McpAuthorizationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class McpConnectUiState(
    val isCompleting: Boolean = false,
    val errorMessage: String? = null,
    /** 連携の完了後にブラウザを遷移させる先。設定されたら画面側で遷移する */
    val redirectUri: String? = null,
)

/** MCP クライアント（Claude Code 等）との連携画面。ユーザーの明示的な操作で連携を完了する */
class McpConnectViewModel(
    private val mcpAuthorizationRepository: McpAuthorizationRepository,
) : ViewModel() {
    var uiState by mutableStateOf(McpConnectUiState())
        private set

    /**
     * 連携を完了する。
     *
     * 成功時は isCompleting を戻さない。遷移するまでの間にボタンが再度押せる状態に戻ると、
     * 使用済みの externalAuthId で再送してエラーになるため。
     */
    fun onContinue(externalAuthId: String?) {
        if (uiState.isCompleting) return
        if (externalAuthId.isNullOrBlank()) {
            uiState = uiState.copy(errorMessage = INVALID_LINK_MESSAGE)
            return
        }
        uiState = uiState.copy(isCompleting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val redirectUri = mcpAuthorizationRepository.complete(externalAuthId)
                uiState = uiState.copy(redirectUri = redirectUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState =
                    uiState.copy(
                        isCompleting = false,
                        errorMessage = "連携を完了できませんでした。連携元のアプリからやり直してください（${e.message}）",
                    )
            }
        }
    }

    companion object {
        internal const val INVALID_LINK_MESSAGE = "連携用のリンクが正しくありません。連携元のアプリからやり直してください"
    }
}
