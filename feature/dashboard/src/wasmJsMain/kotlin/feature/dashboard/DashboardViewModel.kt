package feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.FeedingRepository
import core.network.GarbageScheduleRepository
import core.network.PetRepository
import core.ui.util.currentTimeJs
import core.ui.util.currentYearJs
import core.ui.util.dayOfWeekIndexJs
import core.ui.util.feedingDateJs
import core.ui.util.formattedTodayJs
import core.ui.util.todayDateJs
import core.ui.util.weekOfMonthJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.CollectionFrequency
import model.FeedingLog
import model.GarbageType
import model.GarbageTypeSchedule
import model.MealTime

data class DashboardUiState(
    val feedingLog: FeedingLog = FeedingLog(date = ""),
    val isLoading: Boolean = true,
    val error: String? = null,
    val petName: String? = null,
    val todayGarbageTypes: List<GarbageType> = emptyList(),
    val currentTime: String = "",
    val currentYear: String = "",
    val dateWithDay: String = "",
)

class DashboardViewModel(
    private val petRepository: PetRepository,
    private val feedingRepository: FeedingRepository,
    private val garbageScheduleRepository: GarbageScheduleRepository,
) : ViewModel() {
    private var today: String = feedingDateJs().toString()
    private var petId: String? = null
    private var cachedSchedules: List<GarbageTypeSchedule> = emptyList()
    private var trackedDate: String = todayDateJs().toString()
    private var trackedFeedingDate: String = today

    var uiState by mutableStateOf(
        DashboardUiState(
            feedingLog = FeedingLog(date = today),
            currentTime = currentTimeJs().toString(),
            currentYear = currentYearJs().toString(),
            dateWithDay = formattedTodayJs().toString(),
        ),
    )
        private set

    init {
        viewModelScope.launch {
            try {
                val pet = petRepository.getPets().firstOrNull()
                petId = pet?.id
                uiState = uiState.copy(petName = pet?.name)
                loadToday()
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
        loadGarbageSchedule()
        startDateChangePolling()
    }

    private fun startDateChangePolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                uiState = uiState.copy(currentTime = currentTimeJs().toString())
                val newDate = todayDateJs().toString()
                if (newDate != trackedDate) {
                    trackedDate = newDate
                    uiState =
                        uiState.copy(
                            currentYear = currentYearJs().toString(),
                            dateWithDay = formattedTodayJs().toString(),
                        )
                    refreshGarbageForToday()
                }
                val newFeedingDate = feedingDateJs().toString()
                if (newFeedingDate != trackedFeedingDate) {
                    trackedFeedingDate = newFeedingDate
                    onRefreshFeeding()
                }
            }
        }
    }

    private suspend fun loadToday() {
        val id = petId ?: return
        try {
            val log = feedingRepository.getFeedingLog(id, today)
            uiState = uiState.copy(feedingLog = log, isLoading = false)
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message, isLoading = false)
        }
    }

    private fun loadGarbageSchedule() {
        viewModelScope.launch {
            try {
                cachedSchedules = garbageScheduleRepository.getSchedules()
                refreshGarbageForToday()
            } catch (_: Exception) {
                // ゴミ出し情報取得失敗は無視
            }
        }
    }

    private fun refreshGarbageForToday() {
        val dayOfWeek = dayOfWeekIndexJs()
        val weekOfMonth = weekOfMonthJs()
        uiState =
            uiState.copy(
                todayGarbageTypes =
                    cachedSchedules.filter { schedule ->
                        dayOfWeek in schedule.daysOfWeek && matchesFrequency(schedule.frequency, weekOfMonth)
                    }.map { it.garbageType },
            )
    }

    private fun matchesFrequency(
        frequency: CollectionFrequency,
        weekOfMonth: Int,
    ): Boolean =
        when (frequency) {
            CollectionFrequency.WEEKLY -> true
            CollectionFrequency.WEEK_1_3 -> weekOfMonth == 1 || weekOfMonth == 3
            CollectionFrequency.WEEK_2_4 -> weekOfMonth == 2 || weekOfMonth == 4
        }

    fun onRefreshFeeding() {
        val newDate = feedingDateJs().toString()
        today = newDate
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, feedingLog = FeedingLog(date = today))
            loadToday()
        }
    }

    fun onFeed(mealTime: MealTime) {
        val id = petId ?: return
        viewModelScope.launch {
            try {
                val feeding = feedingRepository.feed(id, today, mealTime)
                uiState =
                    uiState.copy(
                        feedingLog =
                            uiState.feedingLog.copy(
                                feedings = uiState.feedingLog.feedings + (mealTime to feeding),
                            ),
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }
}
