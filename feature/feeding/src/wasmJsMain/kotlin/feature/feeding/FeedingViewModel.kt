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
import model.Pet

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
    var pet by mutableStateOf<Pet?>(null)
        private set

    init {
        scope.launch {
            try {
                val pets: List<Pet> = authenticatedClient.get("/api/pets").body()
                pet = pets.firstOrNull()
                loadLog(selectedDate)
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    fun loadLog(date: String) {
        val petId = pet?.id ?: return
        selectedDate = date
        loading = true
        error = null
        scope.launch {
            try {
                log = authenticatedClient.get("/api/pets/$petId/feeding/$date").body()
                noteDraft = log.note
                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    fun feed(mealTime: MealTime) {
        val petId = pet?.id ?: return
        scope.launch {
            try {
                val feeding: Feeding =
                    authenticatedClient.put(
                        "/api/pets/$petId/feeding/$selectedDate/${mealTime.name.lowercase()}",
                    ).body()
                log =
                    log.copy(
                        feedings = log.feedings.toMutableMap().apply { put(mealTime, feeding) },
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
        val petId = pet?.id ?: return
        scope.launch {
            try {
                authenticatedClient.put("/api/pets/$petId/feeding/$selectedDate/note") {
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
}
