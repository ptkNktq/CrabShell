package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.LoginHistoryRepository
import kotlinx.coroutines.launch
import model.LoginEvent

data class LoginHistoryUiState(
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val loadErrorMessage: String? = null,
    val events: List<LoginEvent> = emptyList(),
)

class LoginHistoryViewModel(
    private val loginHistoryRepository: LoginHistoryRepository,
) : ViewModel() {
    var uiState by mutableStateOf(LoginHistoryUiState())
        private set

    init {
        loadHistory()
    }

    fun loadHistory() {
        uiState = uiState.copy(isLoading = true, loadError = false, loadErrorMessage = null)
        viewModelScope.launch {
            try {
                val events = loginHistoryRepository.getLoginHistory()
                uiState = uiState.copy(isLoading = false, events = events)
            } catch (e: Exception) {
                uiState =
                    uiState.copy(
                        isLoading = false,
                        loadError = true,
                        loadErrorMessage = e.message ?: "ログイン履歴の取得に失敗しました",
                    )
            }
        }
    }
}
