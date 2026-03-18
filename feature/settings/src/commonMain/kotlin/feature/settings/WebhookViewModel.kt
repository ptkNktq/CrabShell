package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.WebhookRepository
import kotlinx.coroutines.launch
import model.WebhookSettings

data class WebhookUiState(
    val url: String = "",
    val enabled: Boolean = false,
    val events: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val loadErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
)

class WebhookViewModel(
    private val webhookRepository: WebhookRepository,
) : ViewModel() {
    var uiState by mutableStateOf(WebhookUiState())
        private set

    init {
        loadSettings()
    }

    fun loadSettings() {
        uiState = uiState.copy(isLoading = true, loadError = false, message = null)
        viewModelScope.launch {
            try {
                val settings = webhookRepository.getSettings()
                uiState =
                    uiState.copy(
                        url = settings.url,
                        enabled = settings.enabled,
                        events = settings.events,
                        isLoading = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, loadError = true, loadErrorMessage = e.message)
            }
        }
    }

    fun onUrlChanged(url: String) {
        uiState = uiState.copy(url = url, message = null)
    }

    fun onEnabledChanged(enabled: Boolean) {
        uiState = uiState.copy(enabled = enabled, message = null)
    }

    fun onToggleEvent(event: String) {
        val newEvents =
            if (event in uiState.events) {
                uiState.events - event
            } else {
                uiState.events + event
            }
        uiState = uiState.copy(events = newEvents, message = null)
    }

    fun onSave() {
        uiState = uiState.copy(isSaving = true, message = null)
        viewModelScope.launch {
            try {
                val settings =
                    WebhookSettings(
                        url = uiState.url,
                        enabled = uiState.enabled,
                        events = uiState.events,
                    )
                webhookRepository.updateSettings(settings)
                uiState = uiState.copy(isSaving = false, message = "保存しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, message = "保存失敗: ${e.message}")
            }
        }
    }
}
