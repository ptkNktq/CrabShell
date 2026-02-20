package feature.quest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.PointRepository
import core.network.QuestRepository
import core.network.RewardRepository
import kotlinx.coroutines.launch
import model.CreateQuestRequest
import model.CreateRewardRequest
import model.PointHistory
import model.Quest
import model.QuestCategory
import model.QuestStatus
import model.Reward
import model.UserPoints

enum class QuestTab {
    Board,
    Rewards,
    History,
}

data class QuestUiState(
    val quests: List<Quest> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCreating: Boolean = false,
    val currentTab: QuestTab = QuestTab.Board,
    val myPoints: UserPoints? = null,
    val history: List<PointHistory> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val isCreatingReward: Boolean = false,
    val isAiAvailable: Boolean = false,
    val isGenerating: Boolean = false,
) {
    /** 同時発行上限（Open + Accepted が10件未満なら作成可能） */
    val canCreateQuest: Boolean
        get() = quests.count { it.status == QuestStatus.Open || it.status == QuestStatus.Accepted } < 10
}

class QuestViewModel(
    private val questRepository: QuestRepository,
    private val pointRepository: PointRepository,
    private val rewardRepository: RewardRepository,
) : ViewModel() {
    var uiState by mutableStateOf(QuestUiState())
        private set

    init {
        loadQuests()
        loadPoints()
        checkAiAvailability()
    }

    private fun checkAiAvailability() {
        viewModelScope.launch {
            val available = questRepository.isAiAvailable()
            uiState = uiState.copy(isAiAvailable = available)
        }
    }

    fun loadQuests() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val quests =
                    questRepository
                        .getQuests(null)
                        .filter { it.status != QuestStatus.Verified }
                uiState = uiState.copy(quests = quests, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    private fun loadPoints() {
        viewModelScope.launch {
            try {
                val points = pointRepository.getMyPoints()
                uiState = uiState.copy(myPoints = points)
            } catch (_: Exception) {
                // ポイント取得失敗は致命的でないので無視
            }
        }
    }

    fun onSelectTab(tab: QuestTab) {
        uiState = uiState.copy(currentTab = tab)
        loadPoints()
        when (tab) {
            QuestTab.Board -> loadQuests()
            QuestTab.Rewards -> loadRewards()
            QuestTab.History -> loadHistory()
        }
    }

    private fun loadRewards() {
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val rewards = rewardRepository.getRewards()
                uiState = uiState.copy(rewards = rewards, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    private fun loadHistory() {
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val history = pointRepository.getHistory()
                uiState = uiState.copy(history = history, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun onToggleCreateForm() {
        uiState = uiState.copy(isCreating = !uiState.isCreating)
    }

    fun onGenerateText(
        title: String,
        description: String,
        category: QuestCategory,
        rewardPoints: Int,
        deadline: String?,
        onResult: (generatedTitle: String, generatedDescription: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        uiState = uiState.copy(isGenerating = true)
        viewModelScope.launch {
            try {
                val response = questRepository.generateQuestText(title, description, category, rewardPoints, deadline)
                onResult(response.generatedTitle, response.generatedDescription)
            } catch (e: Exception) {
                onError("AI 生成に失敗しました: ${e.message}")
            } finally {
                uiState = uiState.copy(isGenerating = false)
            }
        }
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

    fun onVerifyQuest(id: String) {
        viewModelScope.launch {
            try {
                questRepository.verifyQuest(id)
                uiState = uiState.copy(quests = uiState.quests.filter { it.id != id })
                loadPoints()
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

    fun onExchangeReward(id: String) {
        viewModelScope.launch {
            try {
                rewardRepository.exchangeReward(id)
                loadPoints()
                loadRewards()
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onToggleCreateReward() {
        uiState = uiState.copy(isCreatingReward = !uiState.isCreatingReward)
    }

    fun onCreateReward(
        name: String,
        description: String,
        cost: Int,
    ) {
        viewModelScope.launch {
            try {
                val reward = rewardRepository.createReward(CreateRewardRequest(name, description, cost))
                uiState =
                    uiState.copy(
                        rewards = uiState.rewards + reward,
                        isCreatingReward = false,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onDeleteReward(id: String) {
        viewModelScope.launch {
            try {
                rewardRepository.deleteReward(id)
                uiState = uiState.copy(rewards = uiState.rewards.filter { it.id != id })
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun onDismissError() {
        uiState = uiState.copy(error = null)
    }
}
