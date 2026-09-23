package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.AuthRepository
import core.auth.AuthStateHolder
import core.network.PasskeyRepository
import kotlinx.coroutines.launch

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
    authRepository: AuthRepository,
    private val passkeyRepository: PasskeyRepository,
    private val authStateHolder: AuthStateHolder,
    private val signInService: SignInService,
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

    // サインイン成功時は認証状態の切り替わりで本 ViewModel ごと破棄されるため、
    // サインイン本体と履歴記録は SignInService 側（画面より長く生存するスコープ）で行う。
    // 成功時は isLoading を戻さない。認証状態が切り替わるまでの間にボタンが再度押せる状態に戻り、
    // 二重送信の隙ができるのを防ぐため。
    fun onSignIn() {
        if (uiState.email.isBlank() || uiState.password.isBlank()) {
            uiState = uiState.copy(errorMessage = "メールアドレスとパスワードを入力してください")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            signInService
                .signInWithEmail(uiState.email, uiState.password)
                .onFailure { showSignInError(it, "認証に失敗しました") }
        }
    }

    private fun showSignInError(
        e: Throwable,
        defaultMessage: String,
    ) {
        uiState = uiState.copy(isLoading = false, errorMessage = e.message ?: defaultMessage)
    }

    fun onPasskeySignIn() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            passkeyRepository
                .authenticateWithPasskey()
                .onSuccess { customToken ->
                    authStateHolder.signedInViaPasskey = true
                    signInService
                        .signInWithCustomToken(customToken)
                        .onFailure { showSignInError(it, "認証に失敗しました") }
                }.onFailure { showSignInError(it, "パスキー認証に失敗しました") }
        }
    }
}
