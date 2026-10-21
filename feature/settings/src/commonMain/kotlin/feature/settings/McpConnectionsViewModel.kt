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
        if (uiState.updatingId != null) return
        uiState = uiState.copy(updatingId = id, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            try {
                val updated = if (disable) repository.disable(id) else repository.enable(id)
                uiState =
                    uiState.copy(
                        connections = uiState.connections.map { if (it.id == updated.id) updated else it },
                        confirmingDisableId = null,
                        successMessage = "「${updated.clientName}」を${if (disable) "無効" else "有効"}にしました",
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
        internal const val DISABLE_FAILED_MESSAGE = "無効にできませんでした。時間をおいて再度お試しください"
        internal const val ENABLE_FAILED_MESSAGE = "有効にできませんでした。時間をおいて再度お試しください"
    }
}
