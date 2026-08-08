package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.MoneyDueDateNotificationRepository
import kotlinx.coroutines.launch
import model.MoneyDueDateNotificationSettings

data class MoneyDueDateNotificationUiState(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    val daysBefore: String = "1",
    val notifyHour: String = "23",
    val prefix: String = "",
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val loadErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
) {
    val isDaysBeforeValid: Boolean
        get() = (daysBefore.toIntOrNull() ?: -1) >= 0

    val isNotifyHourValid: Boolean
        get() {
            val h = notifyHour.toIntOrNull() ?: return false
            return h in 0..23
        }
}

class MoneyDueDateNotificationViewModel(
    private val moneyDueDateNotificationRepository: MoneyDueDateNotificationRepository,
) : ViewModel() {
    var uiState by mutableStateOf(MoneyDueDateNotificationUiState())
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
                val settings = moneyDueDateNotificationRepository.getSettings()
                uiState =
                    uiState.copy(
                        enabled = settings.enabled,
                        webhookUrl = settings.webhookUrl,
                        daysBefore = settings.daysBefore.toString(),
                        notifyHour = settings.notifyHour.toString(),
                        prefix = settings.prefix,
                        isLoading = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, loadError = true, loadErrorMessage = e.message)
            }
        }
    }

    fun onEnabledChanged(enabled: Boolean) {
        uiState = uiState.copy(enabled = enabled, statusMessage = null)
    }

    fun onWebhookUrlChanged(url: String) {
        uiState = uiState.copy(webhookUrl = url, statusMessage = null)
    }

    fun onDaysBeforeChanged(daysBefore: String) {
        uiState = uiState.copy(daysBefore = daysBefore.filter { it.isDigit() }.take(3), statusMessage = null)
    }

    fun onNotifyHourChanged(notifyHour: String) {
        uiState = uiState.copy(notifyHour = notifyHour.filter { it.isDigit() }.take(2), statusMessage = null)
    }

    fun onPrefixChanged(prefix: String) {
        uiState = uiState.copy(prefix = prefix, statusMessage = null)
    }

    fun onSave() {
        val daysBefore = uiState.daysBefore.toIntOrNull() ?: return
        val notifyHour = uiState.notifyHour.toIntOrNull() ?: return
        uiState = uiState.copy(isSaving = true, statusMessage = null)
        viewModelScope.launch {
            try {
                moneyDueDateNotificationRepository.updateSettings(
                    MoneyDueDateNotificationSettings(
                        enabled = uiState.enabled,
                        webhookUrl = uiState.webhookUrl,
                        daysBefore = daysBefore,
                        notifyHour = notifyHour,
                        prefix = uiState.prefix,
                    ),
                )
                uiState = uiState.copy(isSaving = false, statusMessage = "保存しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, statusMessage = "保存に失敗しました: ${e.message}")
            }
        }
    }

    fun onTest() {
        uiState = uiState.copy(isTesting = true, statusMessage = null)
        viewModelScope.launch {
            try {
                moneyDueDateNotificationRepository.test()
                uiState = uiState.copy(isTesting = false, statusMessage = "テスト送信しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isTesting = false, statusMessage = "テスト送信失敗: ${e.message}")
            }
        }
    }
}
