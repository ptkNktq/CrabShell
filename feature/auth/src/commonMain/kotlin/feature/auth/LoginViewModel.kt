package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.AuthRepository
import core.auth.AuthStateHolder
import core.common.AppLogger
import core.network.LoginHistoryRepository
import core.network.PasskeyRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import model.LoginMethod

private const val RECORD_LOGIN_TIMEOUT_MS = 2_000L

enum class LoginMode {
    PASSKEY,
    EMAIL_PASSWORD,
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginMode: LoginMode = LoginMode.PASSKEY,
    val isWebAuthnSupported: Boolean = true,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val passkeyRepository: PasskeyRepository,
    private val authStateHolder: AuthStateHolder,
    private val loginHistoryRepository: LoginHistoryRepository,
) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        val webAuthnSupported = authRepository.isWebAuthnSupported()
        uiState =
            uiState.copy(
                isWebAuthnSupported = webAuthnSupported,
                loginMode = if (webAuthnSupported) LoginMode.PASSKEY else LoginMode.EMAIL_PASSWORD,
            )
    }

    fun onEmailChanged(value: String) {
        uiState = uiState.copy(email = value, errorMessage = null)
    }

    fun onPasswordChanged(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun onTogglePasswordVisibility() {
        uiState = uiState.copy(isPasswordVisible = !uiState.isPasswordVisible)
    }

    fun onSwitchToPasskey() {
        uiState = uiState.copy(loginMode = LoginMode.PASSKEY, errorMessage = null)
    }

    fun onSwitchToEmailPassword() {
        uiState = uiState.copy(loginMode = LoginMode.EMAIL_PASSWORD, errorMessage = null)
    }

    /**
     * ログイン履歴を記録する。タイムアウト（[RECORD_LOGIN_TIMEOUT_MS]）付きで待機し、
     * 失敗や遅延があってもログイン自体はブロックしない。
     */
    private suspend fun recordLoginWithTimeout(method: LoginMethod) {
        withTimeoutOrNull(RECORD_LOGIN_TIMEOUT_MS) {
            runCatching { loginHistoryRepository.recordLogin(method) }
                .onFailure { AppLogger.w("LoginViewModel", "Failed to record login history: ${it.message}") }
        } ?: AppLogger.w("LoginViewModel", "recordLogin timed out after ${RECORD_LOGIN_TIMEOUT_MS}ms")
    }

    fun onSignIn() {
        if (uiState.email.isBlank() || uiState.password.isBlank()) {
            uiState = uiState.copy(errorMessage = "メールアドレスとパスワードを入力してください")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.signIn(uiState.email, uiState.password)
            if (result.isSuccess) {
                recordLoginWithTimeout(LoginMethod.EMAIL)
            }
            uiState = uiState.copy(isLoading = false)
            if (result.isFailure) {
                uiState =
                    uiState.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "認証に失敗しました",
                    )
            }
        }
    }

    fun onPasskeySignIn() {
        if (uiState.email.isBlank()) {
            uiState = uiState.copy(errorMessage = "メールアドレスを入力してください")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            passkeyRepository
                .authenticateWithPasskey(uiState.email)
                .onSuccess { customToken ->
                    authStateHolder.signedInViaPasskey = true
                    val result = authRepository.signInWithCustomToken(customToken)
                    if (result.isSuccess) {
                        recordLoginWithTimeout(LoginMethod.PASSKEY)
                    }
                    uiState = uiState.copy(isLoading = false)
                    if (result.isFailure) {
                        uiState =
                            uiState.copy(
                                errorMessage = result.exceptionOrNull()?.message ?: "認証に失敗しました",
                            )
                    }
                }.onFailure { e ->
                    uiState =
                        uiState.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "パスキー認証に失敗しました",
                        )
                }
        }
    }
}
