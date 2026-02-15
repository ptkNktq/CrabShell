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

data class FeedingUiState(
    val log: FeedingLog = FeedingLog(date = ""),
    val selectedDate: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val noteDraft: String = "",
    val pet: Pet? = null,
)

class FeedingViewModel(
    private val scope: CoroutineScope,
    private val petRepository: PetRepository,
    private val feedingRepository: FeedingRepository,
) {
    var uiState by mutableStateOf(
        FeedingUiState(
            log = FeedingLog(date = todayDateJs().toString()),
            selectedDate = todayDateJs().toString(),
        ),
    )
        private set

    init {
        scope.launch {
            try {
                val pet = petRepository.getPets().firstOrNull()
                uiState = uiState.copy(pet = pet)
                onLoadLog(uiState.selectedDate)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun onLoadLog(date: String) {
        val petId = uiState.pet?.id ?: return
        uiState = uiState.copy(selectedDate = date, isLoading = true, error = null)
        scope.launch {
            try {
                val log = feedingRepository.getFeedingLog(petId, date)
                uiState = uiState.copy(log = log, noteDraft = log.note, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun onFeed(mealTime: MealTime) {
        val petId = uiState.pet?.id ?: return
        scope.launch {
            try {
                val feeding = feedingRepository.feed(petId, uiState.selectedDate, mealTime)
                uiState =
                    uiState.copy(
                        log =
                            uiState.log.copy(
                                feedings = uiState.log.feedings.toMutableMap().apply { put(mealTime, feeding) },
                            ),
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onUpdateNoteDraft(text: String) {
        uiState = uiState.copy(noteDraft = text)
    }

    fun onSaveNote() {
        val petId = uiState.pet?.id ?: return
        scope.launch {
            try {
                feedingRepository.updateNote(petId, uiState.selectedDate, uiState.noteDraft)
                uiState = uiState.copy(log = uiState.log.copy(note = uiState.noteDraft))
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onGoToPreviousDay() {
        onLoadLog(shiftDateJs(uiState.selectedDate.toJsString(), -1).toString())
    }

    fun onGoToNextDay() {
        onLoadLog(shiftDateJs(uiState.selectedDate.toJsString(), 1).toString())
    }
}
