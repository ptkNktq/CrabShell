package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.common.AppLogger
import core.network.ApiResponseException
import core.network.McpAuthorizationRepository
import core.network.McpConnectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import model.McpAuthorizationErrors

data class McpConnectUiState(
    val isCompleting: Boolean = false,
    val errorMessage: String? = null,
    /** 連携の完了後にブラウザを遷移させる先。設定されたら画面側で遷移する */
    val redirectUri: String? = null,
    /** 連携をキャンセルした。以降は連携を続けられず、タブを閉じてもらうだけの画面になる */
    val isCancelled: Boolean = false,
    /** 無効にした連携アプリ。連携し直すアプリをここで有効にしてもらう */
    val disabledConnections: DisabledConnectionsState = DisabledConnectionsState(),
    /** 無効にした連携アプリを読み込み中。終わるまで（失敗した場合を除く）連携を続けられない */
    val isLoadingDisabledConnections: Boolean = true,
    /** 無効にした連携アプリを読み込めなかった。連携は続けられるが、気付かずに続けないよう注意と再試行を出す */
    val disabledConnectionsLoadFailed: Boolean = false,
)

/** MCP クライアント（Claude Code 等）との連携画面。ユーザーの明示的な操作で連携を完了する */
class McpConnectViewModel(
    private val mcpAuthorizationRepository: McpAuthorizationRepository,
    private val mcpConnectionRepository: McpConnectionRepository,
) : ViewModel() {
    var uiState by mutableStateOf(McpConnectUiState())
        private set

    init {
        loadDisabledConnections()
    }

    /**
     * 無効にした連携アプリを取得する（無効にした日の新しい順）。
     * 取得できなくても連携は続けられるが、無効にしたアプリがあるかどうか分からないことを伝えて再試行できるようにする。
     * 読み込み中に続けられると、無効にしたアプリだと気付かずに連携してしまうため、終わるまで続けさせない。
     * 読み込み中フラグは launch の前に立てる（再試行の連打で重ねて読み込まないように）
     */
    private fun loadDisabledConnections() {
        uiState = uiState.copy(isLoadingDisabledConnections = true, disabledConnectionsLoadFailed = false)
        viewModelScope.launch {
            try {
                // 無効にした日の新しい順はサーバーが時刻で並べて返す
                val disabled = mcpConnectionRepository.getDisabledConnections().connections
                uiState = uiState.copy(disabledConnections = uiState.disabledConnections.copy(connections = disabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to load MCP connections: ${e.message}")
                uiState = uiState.copy(disabledConnectionsLoadFailed = true)
            } finally {
                uiState = uiState.copy(isLoadingDisabledConnections = false)
            }
        }
    }

    /** 読み込めなかった無効にした連携アプリを、もう一度読み込む */
    fun onRetryLoadDisabledConnections() {
        if (uiState.isLoadingDisabledConnections || uiState.isCompleting || uiState.isCancelled) return
        loadDisabledConnections()
    }

    fun onToggleDisabledExpanded() {
        val disabled = uiState.disabledConnections
        // 閉じるときは絞り込みも消す（次に開いたときに一部しか出ていないように見せない）
        uiState = uiState.copy(disabledConnections = disabled.copy(isExpanded = !disabled.isExpanded, filterQuery = ""))
    }

    fun onDisabledFilterQueryChange(query: String) {
        uiState = uiState.copy(disabledConnections = uiState.disabledConnections.copy(filterQuery = query))
    }

    /** 無効にした連携アプリを有効に戻す。実行中フラグは launch の前に立て、完了まで他の行を押せなくする */
    fun onEnableConnection(id: String) {
        val disabled = uiState.disabledConnections
        if (disabled.enablingId != null || id in disabled.enabledIds || uiState.isCompleting || uiState.isCancelled) return
        uiState = uiState.copy(disabledConnections = disabled.copy(enablingId = id, errorMessage = null))
        viewModelScope.launch {
            try {
                mcpConnectionRepository.enable(id)
                val current = uiState.disabledConnections
                uiState = uiState.copy(disabledConnections = current.copy(enabledIds = current.enabledIds + id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(disabledConnections = uiState.disabledConnections.copy(errorMessage = ENABLE_FAILED_MESSAGE))
            } finally {
                uiState = uiState.copy(disabledConnections = uiState.disabledConnections.copy(enablingId = null))
            }
        }
    }

    /**
     * 連携を完了する。
     *
     * 成功時は isCompleting を戻さない。遷移するまでの間にボタンが再度押せる状態に戻ると、
     * 使用済みの externalAuthId で再送してエラーになるため。
     *
     * 有効化の実行中は受け付けない。有効化より先に連携が完了すると、クライアントが無効のまま `/mcp` を呼んで 401 を受け、
     * クライアントによっては連携し直すまで接続しなくなるため（Claude Code など）。失敗の案内も遷移で見られなくなる。
     */
    fun onContinue(externalAuthId: String?) {
        if (uiState.isCompleting ||
            uiState.isCancelled ||
            uiState.isLoadingDisabledConnections ||
            uiState.disabledConnections.enablingId != null
        ) {
            return
        }
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
                uiState = uiState.copy(isCompleting = false, errorMessage = errorMessageFor(e))
            }
        }
    }

    /** 連携をキャンセルする。連携のために開いた画面なので、アプリの他の画面へは進ませずに終わらせる */
    fun onCancel() {
        if (uiState.isCompleting || uiState.disabledConnections.enablingId != null) return
        uiState = uiState.copy(isCancelled = true, errorMessage = null)
    }

    /** 原因ごとに、ユーザーが次に何をすればよいかを案内する（サーバーのエラー文言はそのまま出さない） */
    private fun errorMessageFor(e: Exception): String =
        when {
            e is ApiResponseException && e.message == McpAuthorizationErrors.EMAIL_NOT_REGISTERED -> MISSING_EMAIL_MESSAGE
            e is ApiResponseException && e.message == McpAuthorizationErrors.INVALID_EXTERNAL_AUTH_ID -> INVALID_LINK_MESSAGE
            e is ApiResponseException && e.statusCode == TOO_MANY_REQUESTS -> TOO_MANY_REQUESTS_MESSAGE
            else -> FAILED_MESSAGE
        }

    companion object {
        private const val TAG = "McpConnectViewModel"
        private const val TOO_MANY_REQUESTS = 429

        internal const val MISSING_EMAIL_MESSAGE = "アカウントにメールアドレスが登録されていないため連携できません。管理者に連絡してください"
        internal const val TOO_MANY_REQUESTS_MESSAGE = "操作が多すぎます。しばらく待ってから、連携元のアプリでやり直してください"
        internal const val FAILED_MESSAGE = "連携を完了できませんでした。連携元のアプリからやり直してください"
        internal const val INVALID_LINK_MESSAGE = "連携用のリンクが正しくありません。連携元のアプリからやり直してください"
        internal const val ENABLE_FAILED_MESSAGE = "有効にできませんでした。時間をおいて再度お試しください"
    }
}
