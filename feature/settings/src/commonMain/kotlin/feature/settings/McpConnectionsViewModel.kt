package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.McpConnectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import model.McpConnection

data class McpConnectionsUiState(
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val loadErrorMessage: String? = null,
    /** サーバーで MCP 連携が有効か */
    val enabled: Boolean = false,
    val connections: List<McpConnection> = emptyList(),
    /** 再読み込み中（一覧は出したまま取り直す） */
    val isRefreshing: Boolean = false,
    /** 無効化の確認を表示している連携アプリの ID */
    val confirmingDisableId: String? = null,
    /** 無効化・有効化を実行中の連携アプリの ID */
    val updatingId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

/** 設定画面の「連携」。MCP の連携アプリの一覧と、無効化・有効化 */
class McpConnectionsViewModel(
    private val repository: McpConnectionRepository,
) : ViewModel() {
    var uiState by mutableStateOf(McpConnectionsUiState())
        private set

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true, loadError = false, loadErrorMessage = null)
        viewModelScope.launch {
            try {
                val response = repository.getConnections()
                uiState = uiState.copy(isLoading = false, enabled = response.enabled, connections = response.connections)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, loadError = true, loadErrorMessage = e.message ?: LOAD_FAILED_MESSAGE)
            }
        }
    }

    /**
     * 一覧を出したまま取り直す（[load] と違い、読み込み中の表示に切り替えない）。
     * 無効化・有効化と同時に走ると古い一覧で結果を上書きしかねないため、互いに実行中は受け付けない
     */
    fun refresh() {
        if (uiState.isRefreshing || uiState.updatingId != null) return
        uiState = uiState.copy(isRefreshing = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            try {
                val response = repository.getConnections()
                uiState =
                    uiState.copy(
                        enabled = response.enabled,
                        connections = response.connections,
                        // 確認中のアプリが一覧から消えていたら確認も閉じる
                        confirmingDisableId = uiState.confirmingDisableId?.takeIf { id -> response.connections.any { it.id == id } },
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = REFRESH_FAILED_MESSAGE)
            } finally {
                uiState = uiState.copy(isRefreshing = false)
            }
        }
    }

    fun onRequestDisable(id: String) {
        if (uiState.updatingId != null) return
        uiState = uiState.copy(confirmingDisableId = id, errorMessage = null, successMessage = null)
    }

    fun onCancelDisable() {
        if (uiState.updatingId != null) return
        uiState = uiState.copy(confirmingDisableId = null)
    }

    fun onConfirmDisable(id: String) = update(id, disable = true)

    fun onEnable(id: String) = update(id, disable = false)

    /** 実行中フラグは launch の前に立て、完了まで他の操作を受け付けない */
    private fun update(
        id: String,
        disable: Boolean,
    ) {
        if (uiState.updatingId != null || uiState.isRefreshing) return
        uiState = uiState.copy(updatingId = id, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            try {
                val updated = if (disable) repository.disable(id) else repository.enable(id)
                uiState =
                    uiState.copy(
                        connections = uiState.connections.map { if (it.id == updated.id) updated else it },
                        confirmingDisableId = null,
                        successMessage =
                            if (disable) {
                                "「${updated.clientName}」を無効にしました"
                            } else {
                                "「${updated.clientName}」を有効にしました。$ENABLED_RECONNECT_NOTE"
                            },
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = if (disable) DISABLE_FAILED_MESSAGE else ENABLE_FAILED_MESSAGE)
            } finally {
                uiState = uiState.copy(updatingId = null)
            }
        }
    }

    companion object {
        internal const val LOAD_FAILED_MESSAGE = "連携アプリの取得に失敗しました"
        internal const val REFRESH_FAILED_MESSAGE = "再読み込みできませんでした。時間をおいて再度お試しください"
        internal const val DISABLE_FAILED_MESSAGE = "無効にできませんでした。時間をおいて再度お試しください"
        internal const val ENABLE_FAILED_MESSAGE = "有効にできませんでした。時間をおいて再度お試しください"

        // 無効の間に 401 を受けたクライアントは、有効に戻しても再認証するまで接続しないことがある（Claude Code など）。
        // 挙動はクライアントごとに違い、サーバーからは分からないため、有効に戻したときは常に案内する
        internal const val ENABLED_RECONNECT_NOTE =
            "無効の間にこのアプリを使っていた場合は、アプリ側で連携し直すまで使えないことがあります"
    }
}
