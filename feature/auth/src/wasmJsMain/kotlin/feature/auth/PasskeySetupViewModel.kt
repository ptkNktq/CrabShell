package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.PasskeyRepository
import kotlinx.browser.window
import kotlinx.coroutines.launch

data class PasskeySetupUiState(
    val isLoading: Boolean = true,
    val isRegistering: Boolean = false,
    val errorMessage: String? = null,
)

class PasskeySetupViewModel(
    private val passkeyRepository: PasskeyRepository,
) : ViewModel() {
    var uiState by mutableStateOf(PasskeySetupUiState())
        private set

    var setupComplete by mutableStateOf(false)
        private set

    fun checkStatus() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            passkeyRepository
                .getPasskeyStatus()
                .onSuccess { status ->
                    if (status.registered) {
                        window.localStorage.setItem("passkey_registered", "true")
                        setupComplete = true
                    }
                    uiState = uiState.copy(isLoading = false)
                }.onFailure {
                    uiState = uiState.copy(isLoading = false)
                    // ステータス取得失敗時はスキップして先に進める
                    setupComplete = true
                }
        }
    }

    fun onRegisterPasskey() {
        uiState = uiState.copy(isRegistering = true, errorMessage = null)
        viewModelScope.launch {
            passkeyRepository
                .registerPasskey()
                .onSuccess {
                    window.localStorage.setItem("passkey_registered", "true")
                    uiState = uiState.copy(isRegistering = false)
                    setupComplete = true
                }.onFailure { e ->
                    uiState =
                        uiState.copy(
                            isRegistering = false,
                            errorMessage = e.message ?: "パスキーの登録に失敗しました",
                        )
                }
        }
    }

    fun onSkip() {
        setupComplete = true
    }
}
