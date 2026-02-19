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
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCreating: Boolean = false,
) {
    /** 同時発行上限（Open + Accepted が3件未満なら作成可能） */
    val canCreateQuest: Boolean
        get() = quests.count { it.status == QuestStatus.Open || it.status == QuestStatus.Accepted } < 3
}

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
                val quests = questRepository.getQuests(null)
                uiState = uiState.copy(quests = quests, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
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
