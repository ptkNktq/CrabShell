package feature.feeding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.network.authenticatedClient
import core.ui.util.shiftDateJs
import core.ui.util.todayDateJs
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Feeding
import model.FeedingLog
import model.MealTime

class FeedingViewModel(private val scope: CoroutineScope) {
    var log by mutableStateOf(FeedingLog(date = todayDateJs().toString()))
        private set
    var selectedDate by mutableStateOf(todayDateJs().toString())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var noteDraft by mutableStateOf("")
        private set

    init {
        loadLog(selectedDate)
    }

    fun loadLog(date: String) {
        selectedDate = date
        loading = true
        error = null
        scope.launch {
            try {
                log = authenticatedClient.get("/api/feeding/$date").body()
                noteDraft = log.note
                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    fun feed(mealTime: MealTime) {
        scope.launch {
            try {
                val feeding: Feeding = authenticatedClient.put(
                    "/api/feeding/$selectedDate/${mealTime.name.lowercase()}"
                ).body()
                log = log.copy(
                    feedings = log.feedings.toMutableMap().apply { put(mealTime, feeding) }
                )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun updateNoteDraft(text: String) {
        noteDraft = text
    }

    fun saveNote() {
        scope.launch {
            try {
                authenticatedClient.put("/api/feeding/$selectedDate/note") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("note" to noteDraft))
                }
                log = log.copy(note = noteDraft)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun goToPreviousDay() {
        loadLog(shiftDateJs(selectedDate.toJsString(), -1).toString())
    }

    fun goToNextDay() {
        loadLog(shiftDateJs(selectedDate.toJsString(), 1).toString())
    }

    fun goToToday() {
        loadLog(todayDateJs().toString())
    }
}
