package feature.petmanagement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.FeedingSettingsRepository
import core.network.PetRepository
import kotlinx.coroutines.launch
import model.FeedingSettings
import model.MealTime
import model.Pet

data class PetManagementUiState(
    val isLoading: Boolean = true,
    val pets: List<Pet> = emptyList(),
    val editingPetNames: Map<String, String> = emptyMap(),
    val mealOrder: List<MealTime> = MealTime.entries.toList(),
    val mealTimes: Map<MealTime, String> = emptyMap(),
    val reminderEnabled: Boolean = false,
    val reminderDelayMinutes: Int = 30,
    val reminderPrefix: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
)

class PetManagementViewModel(
    private val petRepository: PetRepository,
    private val feedingSettingsRepository: FeedingSettingsRepository,
) : ViewModel() {
    var uiState by mutableStateOf(PetManagementUiState())
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val pets = petRepository.getPets()
                val settings = feedingSettingsRepository.getSettings()
                uiState =
                    uiState.copy(
                        isLoading = false,
                        pets = pets,
                        editingPetNames = pets.associate { it.id to it.name },
                        mealOrder = settings.mealOrder,
                        mealTimes = settings.mealTimes,
                        reminderEnabled = settings.reminderEnabled,
                        reminderDelayMinutes = settings.reminderDelayMinutes,
                        reminderPrefix = settings.reminderPrefix,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, message = "読み込み失敗: ${e.message}")
            }
        }
    }

    fun onPetNameChanged(
        petId: String,
        name: String,
    ) {
        uiState =
            uiState.copy(
                editingPetNames = uiState.editingPetNames + (petId to name),
                message = null,
            )
    }

    fun onSavePetName(petId: String) {
        val name = uiState.editingPetNames[petId] ?: return
        uiState = uiState.copy(isSaving = true, message = null)
        viewModelScope.launch {
            try {
                val updated = petRepository.updatePetName(petId, name)
                uiState =
                    uiState.copy(
                        isSaving = false,
                        pets = uiState.pets.map { if (it.id == petId) updated else it },
                        message = "ペット名を更新しました",
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, message = "更新失敗: ${e.message}")
            }
        }
    }

    fun onMealOrderChanged(order: List<MealTime>) {
        uiState = uiState.copy(mealOrder = order, message = null)
    }

    fun onMoveMealUp(index: Int) {
        if (index <= 0) return
        val newOrder = uiState.mealOrder.toMutableList()
        val temp = newOrder[index]
        newOrder[index] = newOrder[index - 1]
        newOrder[index - 1] = temp
        uiState = uiState.copy(mealOrder = newOrder, message = null)
    }

    fun onMoveMealDown(index: Int) {
        if (index >= uiState.mealOrder.size - 1) return
        val newOrder = uiState.mealOrder.toMutableList()
        val temp = newOrder[index]
        newOrder[index] = newOrder[index + 1]
        newOrder[index + 1] = temp
        uiState = uiState.copy(mealOrder = newOrder, message = null)
    }

    fun onMealTimeChanged(
        mealTime: MealTime,
        time: String,
    ) {
        uiState =
            uiState.copy(
                mealTimes = uiState.mealTimes + (mealTime to time),
                message = null,
            )
    }

    fun onReminderEnabledChanged(enabled: Boolean) {
        uiState = uiState.copy(reminderEnabled = enabled, message = null)
    }

    fun onReminderDelayChanged(minutes: String) {
        val value = minutes.toIntOrNull() ?: return
        uiState = uiState.copy(reminderDelayMinutes = value, message = null)
    }

    fun onReminderPrefixChanged(prefix: String) {
        uiState = uiState.copy(reminderPrefix = prefix, message = null)
    }

    fun onSaveFeedingSettings() {
        uiState = uiState.copy(isSaving = true, message = null)
        viewModelScope.launch {
            try {
                val settings =
                    FeedingSettings(
                        mealOrder = uiState.mealOrder,
                        mealTimes = uiState.mealTimes,
                        reminderEnabled = uiState.reminderEnabled,
                        reminderDelayMinutes = uiState.reminderDelayMinutes,
                        reminderPrefix = uiState.reminderPrefix,
                    )
                feedingSettingsRepository.updateSettings(settings)
                uiState = uiState.copy(isSaving = false, message = "設定を保存しました")
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, message = "保存失敗: ${e.message}")
            }
        }
    }
}
