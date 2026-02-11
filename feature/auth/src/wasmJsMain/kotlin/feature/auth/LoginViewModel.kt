package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class LoginViewModel(private val scope: CoroutineScope) {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var passwordVisible by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun onEmailChanged(value: String) {
        email = value
        errorMessage = null
    }

    fun onPasswordChanged(value: String) {
        password = value
        errorMessage = null
    }

    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    fun signIn() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter email and password"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = AuthRepository.signIn(email, password)
            isLoading = false
            if (result.isFailure) {
                errorMessage = result.exceptionOrNull()?.message ?: "Authentication failed"
            }
        }
    }
}
