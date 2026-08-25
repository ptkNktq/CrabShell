package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.PasskeyRepository
import kotlinx.coroutines.launch
import model.PasskeyCredentialInfo

data class PasskeyManagementUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val isRegistering: Boolean = false,
    val credentialCount: Int = 0,
    val credentials: List<PasskeyCredentialInfo> = emptyList(),
    val deletingCredentialId: Long? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

class PasskeyManagementViewModel(
    private val passkeyRepository: PasskeyRepository,
) : ViewModel() {
    var uiState by mutableStateOf(PasskeyManagementUiState())
        private set

    init {
        loadStatus()
    }

    private fun loadStatus() {
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            val result = passkeyRepository.getPasskeyStatus()
            if (result.isSuccess) {
                val status = result.getOrThrow()
                // サーバーは機能無効時 registered=true, credentialCount=0 を返す
                val available = !(status.registered && status.credentialCount == 0)
                uiState =
                    uiState.copy(
                        isLoading = false,
                        isAvailable = available,
                        credentialCount = status.credentialCount,
                        credentials = if (available) uiState.credentials else emptyList(),
                    )
                if (available) {
                    loadCredentials()
                }
            } else {
                uiState =
                    uiState.copy(
                        isLoading = false,
                        isAvailable = false,
                        credentials = emptyList(),
                    )
            }
        }
    }

    private fun loadCredentials() {
        viewModelScope.launch {
            passkeyRepository.getPasskeyCredentials().onSuccess { credentials ->
                uiState = uiState.copy(credentials = credentials)
            }
            // 一覧取得の失敗は致命的ではないため、既存の一覧を残したまま errorMessage は出さない
        }
    }

    fun onRegisterPasskey() {
        uiState = uiState.copy(isRegistering = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            val result = passkeyRepository.registerPasskey()
            if (result.isSuccess) {
                uiState =
                    uiState.copy(
                        isRegistering = false,
                        successMessage = "パスキーを登録しました",
                    )
                loadStatus()
            } else {
                uiState =
                    uiState.copy(
                        isRegistering = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "パスキーの登録に失敗しました",
                    )
            }
        }
    }

    fun onDeletePasskey(id: Long) {
        uiState = uiState.copy(deletingCredentialId = id, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            val result = passkeyRepository.deletePasskey(id)
            if (result.isSuccess) {
                uiState = uiState.copy(successMessage = "パスキーを削除しました")
                loadStatus()
            } else {
                uiState =
                    uiState.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "パスキーの削除に失敗しました",
                    )
            }
            // 並行削除で他 ID のフラグを消さないよう、自分が立てたときだけ解除する
            if (uiState.deletingCredentialId == id) {
                uiState = uiState.copy(deletingCredentialId = null)
            }
        }
    }
}
