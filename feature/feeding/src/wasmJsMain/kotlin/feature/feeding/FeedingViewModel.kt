package feature.feeding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.network.FeedingRepository
import core.network.PetRepository
import core.ui.util.shiftDateJs
import core.ui.util.todayDateJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
                pet = PetRepository.getPets().firstOrNull()
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
                log = FeedingRepository.getFeedingLog(petId, date)
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
                val feeding = FeedingRepository.feed(petId, selectedDate, mealTime)
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
                FeedingRepository.updateNote(petId, selectedDate, noteDraft)
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
