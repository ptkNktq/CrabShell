package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.GarbageScheduleRepository
import kotlinx.coroutines.launch
import model.CollectionFrequency
import model.GarbageNotificationSettings
import model.GarbageType
import model.GarbageTypeSchedule

data class GarbageScheduleUiState(
    val schedules: List<GarbageTypeSchedule> =
        GarbageType.entries.map { GarbageTypeSchedule(garbageType = it, daysOfWeek = emptyList()) },
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val notificationEnabled: Boolean = false,
    val notificationWebhookUrl: String = "",
    val notificationTime: String = "10:00",
    val notificationPrefix: String = "",
    val notificationLoading: Boolean = true,
    val notificationSaving: Boolean = false,
    val notificationMessage: String? = null,
)

class GarbageScheduleViewModel(
    private val garbageScheduleRepository: GarbageScheduleRepository,
) : ViewModel() {
    var uiState by mutableStateOf(GarbageScheduleUiState())
        private set

    init {
        loadSchedules()
        loadNotificationSettings()
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            try {
                val loaded = garbageScheduleRepository.getSchedules()
                uiState =
                    uiState.copy(
                        schedules =
                            GarbageType.entries.map { type ->
                                loaded.find { it.garbageType == type }
                                    ?: GarbageTypeSchedule(garbageType = type, daysOfWeek = emptyList())
                            },
                        isLoading = false,
                    )
            } catch (_: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    private fun loadNotificationSettings() {
        viewModelScope.launch {
            try {
                val settings = garbageScheduleRepository.getNotificationSettings()
                uiState =
                    uiState.copy(
                        notificationEnabled = settings.enabled,
                        notificationWebhookUrl = settings.webhookUrl,
                        notificationTime = settings.notifyTime,
                        notificationPrefix = settings.prefix,
                        notificationLoading = false,
                    )
            } catch (_: Exception) {
                uiState = uiState.copy(notificationLoading = false)
            }
        }
    }

    fun onToggleDay(
        garbageType: GarbageType,
        dayIndex: Int,
    ) {
        uiState =
            uiState.copy(
                message = null,
                schedules =
                    uiState.schedules.map { schedule ->
                        if (schedule.garbageType == garbageType) {
                            val newDays =
                                if (dayIndex in schedule.daysOfWeek) {
                                    schedule.daysOfWeek - dayIndex
                                } else {
                                    schedule.daysOfWeek + dayIndex
                                }
                            schedule.copy(daysOfWeek = newDays.sorted())
                        } else {
                            schedule
                        }
                    },
            )
    }

    fun onChangeFrequency(
        garbageType: GarbageType,
        frequency: CollectionFrequency,
    ) {
        uiState =
            uiState.copy(
                message = null,
                schedules =
                    uiState.schedules.map { schedule ->
                        if (schedule.garbageType == garbageType) {
                            schedule.copy(frequency = frequency)
                        } else {
                            schedule
                        }
                    },
            )
    }

    fun onSaveSchedule() {
        uiState = uiState.copy(isSaving = true, message = null)
        viewModelScope.launch {
            try {
                garbageScheduleRepository.saveSchedules(uiState.schedules)
                uiState = uiState.copy(isSaving = false, message = "保存しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, message = "保存に失敗しました: ${e.message}")
            }
        }
    }

    fun onNotificationEnabledChanged(enabled: Boolean) {
        uiState = uiState.copy(notificationEnabled = enabled, notificationMessage = null)
    }

    fun onNotificationWebhookUrlChanged(url: String) {
        uiState = uiState.copy(notificationWebhookUrl = url, notificationMessage = null)
    }

    fun onNotificationTimeChanged(time: String) {
        uiState = uiState.copy(notificationTime = time, notificationMessage = null)
    }

    fun onNotificationPrefixChanged(prefix: String) {
        uiState = uiState.copy(notificationPrefix = prefix, notificationMessage = null)
    }

    fun onSaveNotificationSettings() {
        uiState = uiState.copy(notificationSaving = true, notificationMessage = null)
        viewModelScope.launch {
            try {
                garbageScheduleRepository.saveNotificationSettings(
                    GarbageNotificationSettings(
                        enabled = uiState.notificationEnabled,
                        webhookUrl = uiState.notificationWebhookUrl,
                        notifyTime = uiState.notificationTime,
                        prefix = uiState.notificationPrefix,
                    ),
                )
                uiState = uiState.copy(notificationSaving = false, notificationMessage = "保存しました")
            } catch (e: Exception) {
                uiState =
                    uiState.copy(notificationSaving = false, notificationMessage = "保存に失敗しました: ${e.message}")
            }
        }
    }
}
