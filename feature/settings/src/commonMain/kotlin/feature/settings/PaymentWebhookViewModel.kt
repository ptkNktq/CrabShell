package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.PaymentWebhookRepository
import kotlinx.coroutines.launch
import model.PaymentWebhookSettings

data class PaymentWebhookUiState(
    val url: String = "",
    val enabled: Boolean = false,
    val message: String = "",
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val loadErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
)

class PaymentWebhookViewModel(
    private val paymentWebhookRepository: PaymentWebhookRepository,
) : ViewModel() {
    var uiState by mutableStateOf(PaymentWebhookUiState())
        private set

    init {
        loadSettings()
    }

    fun loadSettings() {
        uiState =
            uiState.copy(
                isLoading = true,
                loadError = false,
                loadErrorMessage = null,
                statusMessage = null,
            )
        viewModelScope.launch {
            try {
                val settings = paymentWebhookRepository.getSettings()
                uiState =
                    uiState.copy(
                        url = settings.url,
                        enabled = settings.enabled,
                        message = settings.message,
                        isLoading = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, loadError = true, loadErrorMessage = e.message)
            }
        }
    }

    fun onUrlChanged(url: String) {
        uiState = uiState.copy(url = url, statusMessage = null)
    }

    fun onEnabledChanged(enabled: Boolean) {
        uiState = uiState.copy(enabled = enabled, statusMessage = null)
    }

    fun onMessageChanged(message: String) {
        uiState = uiState.copy(message = message, statusMessage = null)
    }

    fun onSave() {
        uiState = uiState.copy(isSaving = true, statusMessage = null)
        viewModelScope.launch {
            try {
                val settings =
                    PaymentWebhookSettings(
                        url = uiState.url,
                        enabled = uiState.enabled,
                        message = uiState.message,
                    )
                paymentWebhookRepository.updateSettings(settings)
                uiState = uiState.copy(isSaving = false, statusMessage = "保存しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, statusMessage = "保存失敗: ${e.message}")
            }
        }
    }
}
