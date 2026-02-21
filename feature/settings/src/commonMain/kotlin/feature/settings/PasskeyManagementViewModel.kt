package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.PasskeyRepository
import kotlinx.coroutines.launch

data class PasskeyManagementUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val isRegistering: Boolean = false,
    val credentialCount: Int = 0,
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
                    )
            } else {
                uiState =
                    uiState.copy(
                        isLoading = false,
                        isAvailable = false,
                    )
            }
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
}
