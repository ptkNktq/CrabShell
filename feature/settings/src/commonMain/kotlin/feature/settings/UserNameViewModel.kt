package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.UserRepository
import kotlinx.coroutines.launch
import model.User

data class UserNameUiState(
    val users: List<User> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
)

class UserNameViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {
    var uiState by mutableStateOf(UserNameUiState())
        private set

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(users = userRepository.getUsers())
            } catch (_: Exception) {
                // 読み込み失敗は空リストのまま
            }
        }
    }

    fun onUpdateDisplayName(
        uid: String,
        displayName: String,
    ) {
        uiState = uiState.copy(isSaving = true, message = null)
        viewModelScope.launch {
            try {
                val updated = userRepository.updateDisplayName(uid, displayName)
                uiState =
                    uiState.copy(
                        users = uiState.users.map { if (it.uid == uid) updated else it },
                        isSaving = false,
                        message = "保存しました",
                    )
            } catch (e: Exception) {
                uiState =
                    uiState.copy(
                        isSaving = false,
                        message = "保存に失敗しました: ${e.message}",
                    )
            }
        }
    }
}
