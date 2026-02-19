package feature.quest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.QuestRepository
import kotlinx.coroutines.launch
import model.CreateQuestRequest
import model.Quest
import model.QuestCategory
import model.QuestStatus

data class QuestUiState(
    val quests: List<Quest> = emptyList(),
    val selectedCategory: QuestCategory? = null,
    val selectedStatus: QuestStatus? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCreating: Boolean = false,
)

class QuestViewModel(
    private val questRepository: QuestRepository,
) : ViewModel() {
    var uiState by mutableStateOf(QuestUiState())
        private set

    init {
        loadQuests()
    }

    fun loadQuests() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val quests = questRepository.getQuests(uiState.selectedStatus)
                val filtered =
                    uiState.selectedCategory?.let { cat ->
                        quests.filter { it.category == cat }
                    } ?: quests
                uiState = uiState.copy(quests = filtered, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun onFilterCategory(category: QuestCategory?) {
        uiState = uiState.copy(selectedCategory = category)
        loadQuests()
    }

    fun onFilterStatus(status: QuestStatus?) {
        uiState = uiState.copy(selectedStatus = status)
        loadQuests()
    }

    fun onToggleCreateForm() {
        uiState = uiState.copy(isCreating = !uiState.isCreating)
    }

    fun onCreateQuest(
        title: String,
        description: String,
        category: QuestCategory,
        rewardPoints: Int,
        deadline: String?,
    ) {
        viewModelScope.launch {
            try {
                val quest =
                    questRepository.createQuest(
                        CreateQuestRequest(
                            title = title,
                            description = description,
                            category = category,
                            rewardPoints = rewardPoints,
                            deadline = deadline,
                        ),
                    )
                uiState =
                    uiState.copy(
                        quests = listOf(quest) + uiState.quests,
                        isCreating = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onAcceptQuest(id: String) {
        viewModelScope.launch {
            try {
                val updated = questRepository.acceptQuest(id)
                uiState = uiState.copy(quests = uiState.quests.map { if (it.id == id) updated else it })
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onCompleteQuest(id: String) {
        viewModelScope.launch {
            try {
                val updated = questRepository.completeQuest(id)
                uiState = uiState.copy(quests = uiState.quests.map { if (it.id == id) updated else it })
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onVerifyQuest(id: String) {
        viewModelScope.launch {
            try {
                val updated = questRepository.verifyQuest(id)
                uiState = uiState.copy(quests = uiState.quests.map { if (it.id == id) updated else it })
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onDeleteQuest(id: String) {
        viewModelScope.launch {
            try {
                questRepository.deleteQuest(id)
                uiState = uiState.copy(quests = uiState.quests.filter { it.id != id })
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onDismissError() {
        uiState = uiState.copy(error = null)
    }
}
