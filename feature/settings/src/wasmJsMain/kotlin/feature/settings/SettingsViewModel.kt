package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SettingsViewModel(private val scope: CoroutineScope) {
    var currentPassword by mutableStateOf("")
        private set
    var newPassword by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    fun onCurrentPasswordChanged(value: String) {
        currentPassword = value
        errorMessage = null
        successMessage = null
    }

    fun onNewPasswordChanged(value: String) {
        newPassword = value
        errorMessage = null
        successMessage = null
    }

    fun onConfirmPasswordChanged(value: String) {
        confirmPassword = value
        errorMessage = null
        successMessage = null
    }

    fun changePassword() {
        if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            errorMessage = "すべての項目を入力してください"
            return
        }
        if (newPassword != confirmPassword) {
            errorMessage = "新しいパスワードが一致しません"
            return
        }
        if (newPassword.length < 6) {
            errorMessage = "パスワードは6文字以上で入力してください"
            return
        }

        isLoading = true
        errorMessage = null
        successMessage = null
        scope.launch {
            val result = AuthRepository.changePassword(currentPassword, newPassword)
            isLoading = false
            if (result.isSuccess) {
                successMessage = "パスワードを変更しました"
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "パスワードの変更に失敗しました"
            }
        }
    }
}
