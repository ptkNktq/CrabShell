package model

import kotlinx.serialization.Serializable

@Serializable
data class QuestWebhookSettings(
    val url: String = "",
    val enabled: Boolean = false,
    val events: List<String> = emptyList(),
)

object QuestWebhookEvent {
    const val QUEST_CREATED = "quest_created"
    const val QUEST_COMPLETED = "quest_completed"
    const val QUEST_VERIFIED = "quest_verified"

    val all = listOf(QUEST_CREATED, QUEST_COMPLETED, QUEST_VERIFIED)

    fun label(event: String): String =
        when (event) {
            QUEST_CREATED -> "クエスト作成"
            QUEST_COMPLETED -> "クエスト達成"
            QUEST_VERIFIED -> "クエスト承認"
            else -> event
        }
}
