package feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.common.TabResumedEvent
import core.network.FeedingRepository
import core.network.GarbageScheduleRepository
import core.network.PetRepository
import core.ui.util.currentTimeJs
import core.ui.util.currentYearJs
import core.ui.util.dayOfWeekIndexJs
import core.ui.util.feedingDateJs
import core.ui.util.formattedTodayJs
import core.ui.util.todayDateJs
import core.ui.util.tomorrowDayOfWeekIndexJs
import core.ui.util.tomorrowWeekOfMonthJs
import core.ui.util.weekOfMonthJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.FeedingLog
import model.GarbageType
import model.GarbageTypeSchedule
import model.MealTime
import model.resolveGarbageTypes

data class DashboardUiState(
    val feedingLog: FeedingLog = FeedingLog(date = ""),
    val feedingLoading: Boolean = true,
    val feedingError: String? = null,
    val feedingActionError: String? = null,
    val petName: String? = null,
    val todayGarbageTypes: List<GarbageType> = emptyList(),
    val garbageUpdateLabel: String = "毎日 10:00 更新",
    val currentTime: String = "",
    val currentYear: String = "",
    val dateWithDay: String = "",
)

class DashboardViewModel(
    tabResumedEvent: TabResumedEvent,
    private val petRepository: PetRepository,
    private val feedingRepository: FeedingRepository,
    private val garbageScheduleRepository: GarbageScheduleRepository,
) : ViewModel() {
    private var today: String = feedingDateJs().toString()
    private var petId: String? = null
    private var cachedSchedules: List<GarbageTypeSchedule> = emptyList()
    private var trackedDate: String = todayDateJs().toString()
    private var trackedFeedingDate: String = today
    private var lastFeedingHalfHour = -1
    private var garbageRefreshedToday =
        (currentTimeJs().toString().substringBefore(":").toIntOrNull() ?: 0) >= GARBAGE_SWITCH_HOUR

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
                uiState = uiState.copy(feedingError = e.message, feedingLoading = false)
            }
        }
        loadGarbageSchedule()
        startDateChangePolling()
        // バックグラウンド復帰時（トークンリフレッシュ完了後）にデータ再取得
        viewModelScope.launch {
            tabResumedEvent.events.collect { onRefreshFeeding() }
        }
    }

    private fun startDateChangePolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                val timeStr = currentTimeJs().toString()
                uiState = uiState.copy(currentTime = timeStr)
                val newDate = todayDateJs().toString()
                if (newDate != trackedDate) {
                    trackedDate = newDate
                    garbageRefreshedToday = false
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
                // 毎時0分・30分に給餌情報をサイレント更新
                val minute = timeStr.substringAfter(":").toIntOrNull() ?: 0
                val halfHour =
                    timeStr
                        .substringBefore(":")
                        .toIntOrNull()
                        ?.times(2)
                        ?.plus(minute / 30) ?: 0
                if (lastFeedingHalfHour != -1 && halfHour != lastFeedingHalfHour) {
                    silentRefreshFeeding()
                }
                lastFeedingHalfHour = halfHour
                // 更新時刻にゴミ出しスケジュールを再取得
                val hour = timeStr.substringBefore(":").toIntOrNull() ?: 0
                if (hour >= GARBAGE_SWITCH_HOUR && !garbageRefreshedToday) {
                    garbageRefreshedToday = true
                    loadGarbageSchedule()
                }
            }
        }
    }

    private suspend fun loadToday() {
        val id = petId ?: return
        try {
            val log = feedingRepository.getFeedingLog(id, today)
            uiState = uiState.copy(feedingLog = log, feedingLoading = false)
        } catch (e: Exception) {
            uiState = uiState.copy(feedingError = e.message, feedingLoading = false)
        }
    }

    private fun loadGarbageSchedule() {
        viewModelScope.launch {
            try {
                cachedSchedules = garbageScheduleRepository.getSchedules()
            } catch (_: Exception) {
                // ゴミ出し情報取得失敗は無視
            }
            // ゴミ情報の更新時刻は 10:00 固定（通知時刻とは独立）
            refreshGarbageForToday()
        }
    }

    private fun refreshGarbageForToday() {
        val hour = currentTimeJs().toString().substringBefore(":").toIntOrNull() ?: 0
        val isAfterSwitchHour = hour >= GARBAGE_SWITCH_HOUR
        val dayOfWeek = if (isAfterSwitchHour) tomorrowDayOfWeekIndexJs() else dayOfWeekIndexJs()
        val weekOfMonth = if (isAfterSwitchHour) tomorrowWeekOfMonthJs() else weekOfMonthJs()
        uiState = uiState.copy(todayGarbageTypes = resolveGarbageTypes(cachedSchedules, dayOfWeek, weekOfMonth))
    }

    private suspend fun silentRefreshFeeding() {
        val id = petId ?: return
        try {
            val log = feedingRepository.getFeedingLog(id, today)
            uiState = uiState.copy(feedingLog = log)
        } catch (_: Exception) {
            // サイレント更新: 失敗時は古いデータを維持
        }
    }

    fun onRefreshFeeding() {
        val newDate = feedingDateJs().toString()
        today = newDate
        viewModelScope.launch {
            uiState =
                uiState.copy(
                    feedingLoading = true,
                    feedingError = null,
                    feedingActionError = null,
                    feedingLog = FeedingLog(date = today),
                )
            loadToday()
        }
    }

    companion object {
        /** ダッシュボードのゴミ情報を「今日→明日」に切り替える時刻（固定） */
        private const val GARBAGE_SWITCH_HOUR = 10
    }

    fun onFeed(mealTime: MealTime) {
        val id = petId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(feedingActionError = null)
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
                uiState = uiState.copy(feedingActionError = e.message)
            }
        }
    }
}
