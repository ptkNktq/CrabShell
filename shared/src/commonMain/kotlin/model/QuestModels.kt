package model

import kotlinx.serialization.Serializable

@Serializable
enum class QuestStatus {
    Open,
    Accepted,
    Completed,
    Verified,
    Expired,
}

@Serializable
enum class QuestCategory {
    Housework,
    Errand,
    Cooking,
    Cleaning,
    Pet,
    Other,
}

@Serializable
data class Quest(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: QuestCategory = QuestCategory.Other,
    val rewardPoints: Int = 0,
    val creatorUid: String = "",
    val creatorName: String = "",
    val assigneeUid: String? = null,
    val assigneeName: String? = null,
    val status: QuestStatus = QuestStatus.Open,
    val deadline: String? = null,
    val createdAt: String = "",
    val completedAt: String? = null,
)

@Serializable
data class CreateQuestRequest(
    val title: String,
    val description: String,
    val category: QuestCategory,
    val rewardPoints: Int,
    val deadline: String? = null,
)

@Serializable
data class QuestBoard(
    val quests: List<Quest> = emptyList(),
)
