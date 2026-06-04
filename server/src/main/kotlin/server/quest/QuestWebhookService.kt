package server.quest

import com.google.cloud.firestore.Firestore
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Quest
import model.QuestWebhookSettings
import server.util.AbstractWebhookService
import server.util.AbstractWebhookService.Companion.defaultWebhookClient
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.detectWebhookService
import server.util.sanitizeForDiscord
import server.util.sanitizeForSlack
import java.time.Instant

class QuestWebhookService(
    firestore: Firestore,
    client: HttpClient = defaultWebhookClient(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AbstractWebhookService<QuestWebhookSettings>(firestore, "quest", client, dispatcher) {
    override fun defaultSettings() = QuestWebhookSettings()

    override fun fromWebhookMap(map: Map<String, Any?>) =
        QuestWebhookSettings(
            url = map["url"] as? String ?: "",
            enabled = map["enabled"] as? Boolean ?: false,
            events = (map["events"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        )

    override fun toWebhookMap(settings: QuestWebhookSettings) =
        mapOf(
            "url" to settings.url,
            "enabled" to settings.enabled,
            "events" to settings.events,
        )

    /** fire-and-forget でイベントを送信 */
    fun notify(
        event: String,
        quest: Quest,
    ) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank() || event !in settings.events) return@launch

                val payload = buildQuestPayload(settings.url, event, quest)

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Webhook delivery failed for event=$event", e)
            }
        }
    }
}

private val json = Json

/**
 * クエストイベントの payload を URL のサービス種別に応じて生成する。
 * Firestore 非依存の純粋関数。
 */
internal fun buildQuestPayload(
    url: String,
    event: String,
    quest: Quest,
    timestamp: String = Instant.now().toString(),
): String =
    when (detectWebhookService(url)) {
        WebhookServiceType.DISCORD -> json.encodeToString(buildDiscordPayload(event, quest))
        WebhookServiceType.SLACK -> json.encodeToString(buildSlackPayload(event, quest))
        WebhookServiceType.GENERIC -> json.encodeToString(buildGenericPayload(event, quest, timestamp))
    }

private fun eventPrefix(event: String): String =
    when (event) {
        "quest_created" -> "🆕 新しいクエスト"
        "quest_verified" -> "✅ クエスト達成"
        else -> event
    }

private fun buildDiscordPayload(
    event: String,
    quest: Quest,
): DiscordPayload {
    val prefix = eventPrefix(event)
    val safeTitle = sanitizeForDiscord(quest.title)
    val safeDescription = sanitizeForDiscord(quest.description)
    val safeCreatorName = sanitizeForDiscord(quest.creatorName)
    return DiscordPayload(
        embeds =
            listOf(
                DiscordEmbed(
                    title = "$prefix: $safeTitle",
                    description = safeDescription,
                    color = DISCORD_EMBED_COLOR,
                    fields =
                        listOf(
                            DiscordField(name = "報酬", value = "${quest.rewardPoints}pt", inline = true),
                            DiscordField(name = "依頼者", value = safeCreatorName, inline = true),
                        ),
                ),
            ),
    )
}

private fun buildSlackPayload(
    event: String,
    quest: Quest,
): SlackPayload {
    val prefix = eventPrefix(event)
    val safeTitle = sanitizeForSlack(quest.title)
    val safeDescription = sanitizeForSlack(quest.description)
    val safeCreatorName = sanitizeForSlack(quest.creatorName)
    return SlackPayload(
        text = "$prefix: $safeTitle\n$safeDescription\n報酬: ${quest.rewardPoints}pt | 依頼者: $safeCreatorName",
    )
}

private fun buildGenericPayload(
    event: String,
    quest: Quest,
    timestamp: String,
): GenericPayload =
    GenericPayload(
        event = event,
        quest =
            GenericQuestData(
                title = quest.title,
                description = quest.description,
                rewardPoints = quest.rewardPoints,
                creatorName = quest.creatorName,
            ),
        timestamp = timestamp,
    )

// --- Discord ペイロード ---

@Serializable
private data class DiscordPayload(
    val embeds: List<DiscordEmbed>,
)

@Serializable
private data class DiscordEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val fields: List<DiscordField>,
)

@Serializable
private data class DiscordField(
    val name: String,
    val value: String,
    val inline: Boolean = false,
)

// --- Slack ペイロード ---

@Serializable
private data class SlackPayload(
    val text: String,
)

// --- 汎用ペイロード (従来互換) ---

@Serializable
private data class GenericPayload(
    val event: String,
    val quest: GenericQuestData,
    val timestamp: String,
)

@Serializable
private data class GenericQuestData(
    val title: String,
    val description: String,
    val rewardPoints: Int,
    val creatorName: String,
)
