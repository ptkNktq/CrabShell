@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.network.authenticatedClient
import core.ui.util.currentTimeJs
import core.ui.util.currentYearJs
import core.ui.util.dayOfWeekIndexJs
import core.ui.util.feedingDateJs
import core.ui.util.formattedTodayJs
import core.ui.util.todayDateJs
import core.ui.util.weekOfMonthJs
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.CollectionFrequency
import model.Feeding
import model.FeedingLog
import model.GarbageType
import model.GarbageTypeSchedule
import model.MealTime
import model.Pet

@JsFun(
    """(iso) => {
    const d = new Date(iso);
    return d.toLocaleTimeString('ja-JP', {
        hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Tokyo',
    });
}""",
)
external fun toJstHHMM(iso: JsString): JsString

class DashboardViewModel(private val scope: CoroutineScope) {
    private var today: String = feedingDateJs().toString()

    var feedingLog by mutableStateOf(FeedingLog(date = today))
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pet by mutableStateOf<Pet?>(null)
        private set
    var todayGarbageTypes by mutableStateOf<List<GarbageType>>(emptyList())
        private set
    var currentTime by mutableStateOf(currentTimeJs().toString())
        private set
    var currentYear by mutableStateOf(currentYearJs().toString())
        private set
    var dateWithDay by mutableStateOf(formattedTodayJs().toString())
        private set

    private var cachedSchedules: List<GarbageTypeSchedule> = emptyList()
    private var trackedDate: String = todayDateJs().toString()
    private var trackedFeedingDate: String = today

    init {
        scope.launch {
            try {
                val pets: List<Pet> = authenticatedClient.get("/api/pets").body()
                pet = pets.firstOrNull()
                loadToday()
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
        loadGarbageSchedule()
        startDateChangePolling()
    }

    private fun startDateChangePolling() {
        scope.launch {
            while (true) {
                delay(10_000)
                currentTime = currentTimeJs().toString()
                val newDate = todayDateJs().toString()
                if (newDate != trackedDate) {
                    trackedDate = newDate
                    currentYear = currentYearJs().toString()
                    dateWithDay = formattedTodayJs().toString()
                    refreshGarbageForToday()
                }
                val newFeedingDate = feedingDateJs().toString()
                if (newFeedingDate != trackedFeedingDate) {
                    trackedFeedingDate = newFeedingDate
                    refreshFeeding()
                }
            }
        }
    }

    private suspend fun loadToday() {
        val petId = pet?.id ?: return
        try {
            feedingLog = authenticatedClient.get("/api/pets/$petId/feeding/$today").body()
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    private fun loadGarbageSchedule() {
        scope.launch {
            try {
                cachedSchedules = authenticatedClient.get("/api/garbage/schedule").body()
                refreshGarbageForToday()
            } catch (_: Exception) {
                // ゴミ出し情報取得失敗は無視
            }
        }
    }

    private fun refreshGarbageForToday() {
        val dayOfWeek = dayOfWeekIndexJs()
        val weekOfMonth = weekOfMonthJs()
        todayGarbageTypes =
            cachedSchedules.filter { schedule ->
                dayOfWeek in schedule.daysOfWeek && matchesFrequency(schedule.frequency, weekOfMonth)
            }.map { it.garbageType }
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

    fun refreshFeeding() {
        val newDate = feedingDateJs().toString()
        today = newDate
        scope.launch {
            loading = true
            error = null
            feedingLog = FeedingLog(date = today)
            loadToday()
        }
    }

    fun feed(mealTime: MealTime) {
        val petId = pet?.id ?: return
        scope.launch {
            try {
                val feeding: Feeding =
                    authenticatedClient.put(
                        "/api/pets/$petId/feeding/$today/${mealTime.name.lowercase()}",
                    ).body()
                feedingLog =
                    feedingLog.copy(
                        feedings = feedingLog.feedings + (mealTime to feeding),
                    )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}
