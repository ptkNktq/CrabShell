package feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.common.TabResumedEvent
import core.network.FeedingRepository
import core.network.FeedingSettingsRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    val mealOrder: List<MealTime> = listOf(MealTime.LUNCH, MealTime.EVENING, MealTime.MORNING),
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
    private val feedingSettingsRepository: FeedingSettingsRepository,
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
    private var pollingJob: Job? = null

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
        loadFeedingSettings()
        loadGarbageSchedule()
        viewModelScope.launch { restartDateChangePolling() }
        // バックグラウンド復帰時: ブラウザの throttle / freeze により遅れた表示を即座に追い付かせ、
        // ポーリングも cancelAndJoin で古いジョブを確実に停止してから再起動する。
        viewModelScope.launch {
            tabResumedEvent.events.collect {
                refreshTimeAndGarbage()
                onRefreshFeeding()
                loadFeedingSettings()
                restartDateChangePolling()
            }
        }
    }

    private fun loadFeedingSettings() {
        viewModelScope.launch {
            try {
                val settings = feedingSettingsRepository.getSettings()
                uiState = uiState.copy(mealOrder = settings.mealOrder)
            } catch (_: Exception) {
                // 設定取得失敗時はデフォルト順序を維持（ごはん機能自体は動作させる）
            }
        }
    }

    /**
     * ポーリングジョブを停止して再起動する。古いジョブが存在する場合は [cancelAndJoin] で
     * 完了を待ってから新規ジョブを launch するため、呼び出し側は in-flight 競合を意識する必要がない。
     */
    private suspend fun restartDateChangePolling() {
        pollingJob?.cancelAndJoin()
        pollingJob =
            viewModelScope.launch {
                // ポーリング起点の即時同期は init 時の uiState 初期値 / TabResumedEvent ハンドラ側で担当するため、
                // ここは delay から開始し、10 秒周期で時刻・ゴミ・給餌のカードを同期するだけに留める。
                while (true) {
                    delay(10_000)
                    refreshTimeAndGarbage()
                    autoRefreshFeedingOnTick()
                }
            }
    }

    /** 時刻カード・年月日・ゴミバッジなど時刻依存の「非給餌」表示を同期する。 */
    private fun refreshTimeAndGarbage() {
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
        val hour = timeStr.substringBefore(":").toIntOrNull() ?: 0
        if (hour >= GARBAGE_SWITCH_HOUR && !garbageRefreshedToday) {
            garbageRefreshedToday = true
            loadGarbageSchedule()
        }
    }

    /**
     * ポーリング tick で日跨ぎ・半時間跨ぎを検知し、必要な場合のみ給餌ログを再取得する。
     * タブ復帰時は [onRefreshFeeding] で無条件再取得するため本関数を呼ばない。
     */
    private suspend fun autoRefreshFeedingOnTick() {
        val newFeedingDate = feedingDateJs().toString()
        if (newFeedingDate != trackedFeedingDate) {
            // 日跨ぎは全量再取得に任せ、同一 tick 内での半時間判定はスキップ（二重 fetch 回避）
            onRefreshFeeding()
            return
        }
        val halfHour = computeCurrentHalfHour()
        if (lastFeedingHalfHour != -1 && halfHour != lastFeedingHalfHour) {
            silentRefreshFeeding()
        }
        lastFeedingHalfHour = halfHour
    }

    private fun computeCurrentHalfHour(): Int {
        val timeStr = currentTimeJs().toString()
        val minute = timeStr.substringAfter(":").toIntOrNull() ?: 0
        return timeStr
            .substringBefore(":")
            .toIntOrNull()
            ?.times(2)
            ?.plus(minute / 30) ?: 0
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

    /**
     * 給餌ログを無条件に再取得する。手動更新ボタン・タブ復帰・日跨ぎ検知から呼ばれる。
     * 次回 [autoRefreshFeedingOnTick] で日跨ぎ・半時間跨ぎ判定が再発火しないよう tracker も同期する。
     */
    fun onRefreshFeeding() {
        val newDate = feedingDateJs().toString()
        today = newDate
        trackedFeedingDate = newDate
        lastFeedingHalfHour = computeCurrentHalfHour()
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

    companion object {
        /** ダッシュボードのゴミ情報を「今日→明日」に切り替える時刻（固定） */
        private const val GARBAGE_SWITCH_HOUR = 10
    }
}
