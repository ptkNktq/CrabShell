package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.auth.AuthRepository
import kotlinx.coroutines.launch

data class PasswordChangeUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

class PasswordChangeViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    var uiState by mutableStateOf(PasswordChangeUiState())
        private set

    fun onCurrentPasswordChanged(value: String) {
        uiState = uiState.copy(currentPassword = value, errorMessage = null, successMessage = null)
    }

    fun onNewPasswordChanged(value: String) {
        uiState = uiState.copy(newPassword = value, errorMessage = null, successMessage = null)
    }

    fun onConfirmPasswordChanged(value: String) {
        uiState = uiState.copy(confirmPassword = value, errorMessage = null, successMessage = null)
    }

    fun onChangePassword() {
        val state = uiState
        if (state.currentPassword.isBlank() || state.newPassword.isBlank() || state.confirmPassword.isBlank()) {
            uiState = uiState.copy(errorMessage = "すべての項目を入力してください")
            return
        }
        if (state.newPassword != state.confirmPassword) {
            uiState = uiState.copy(errorMessage = "新しいパスワードが一致しません")
            return
        }
        if (state.newPassword.length < 6) {
            uiState = uiState.copy(errorMessage = "パスワードは6文字以上で入力してください")
            return
        }

        uiState = uiState.copy(isLoading = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            val result = authRepository.changePassword(state.currentPassword, state.newPassword)
            if (result.isSuccess) {
                uiState =
                    uiState.copy(
                        isLoading = false,
                        successMessage = "パスワードを変更しました",
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = "",
                    )
            } else {
                uiState =
                    uiState.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "パスワードの変更に失敗しました",
                    )
            }
        }
    }
}
