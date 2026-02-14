package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.network.GarbageScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule

data class GarbageScheduleUiState(
    val schedules: List<GarbageTypeSchedule> =
        GarbageType.entries.map { GarbageTypeSchedule(garbageType = it, daysOfWeek = emptyList()) },
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
)

class GarbageScheduleViewModel(private val scope: CoroutineScope) {
    var uiState by mutableStateOf(GarbageScheduleUiState())
        private set

    init {
        loadSchedules()
    }

    private fun loadSchedules() {
        scope.launch {
            try {
                val loaded = GarbageScheduleRepository.getSchedules()
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
        scope.launch {
            try {
                GarbageScheduleRepository.saveSchedules(uiState.schedules)
                uiState = uiState.copy(isSaving = false, message = "保存しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, message = "保存に失敗しました: ${e.message}")
            }
        }
    }
}
