package server.quest

import com.google.cloud.firestore.Firestore
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Quest
import model.WebhookSettings
import org.slf4j.LoggerFactory
import server.util.await
import java.time.Instant

private val logger = LoggerFactory.getLogger("WebhookService")

class WebhookService(
    private val firestore: Firestore,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val settingsDoc get() = firestore.collection("settings").document("webhook")
    private val scope = CoroutineScope(dispatcher)

    private val client = HttpClient()
    private val json = Json

    suspend fun getSettings(): WebhookSettings {
        val doc = settingsDoc.get().await()
        if (!doc.exists()) return WebhookSettings()
        val data = doc.data ?: return WebhookSettings()
        @Suppress("UNCHECKED_CAST")
        return WebhookSettings(
            url = data["url"] as? String ?: "",
            enabled = data["enabled"] as? Boolean ?: false,
            events = (data["events"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        )
    }

    suspend fun updateSettings(settings: WebhookSettings) {
        settingsDoc
            .set(
                mapOf(
                    "url" to settings.url,
                    "enabled" to settings.enabled,
                    "events" to settings.events,
                ),
            ).await()
    }

    /** fire-and-forget でイベントを送信 */
    fun notify(
        event: String,
        quest: Quest,
    ) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank() || event !in settings.events) return@launch

                val payload = buildPayload(settings.url, event, quest)

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Webhook delivery failed for event=$event: ${e.message}")
            }
        }
    }

    /** URL パターンからサービスを判別し JSON 文字列を生成 */
    private fun buildPayload(
        url: String,
        event: String,
        quest: Quest,
    ): String =
        when (detectService(url)) {
            Service.DISCORD -> json.encodeToString(buildDiscordPayload(event, quest))
            Service.SLACK -> json.encodeToString(buildSlackPayload(event, quest))
            Service.GENERIC -> json.encodeToString(buildGenericPayload(event, quest))
        }

    private fun detectService(url: String): Service {
        val lower = url.lowercase()
        return when {
            "discord.com/api/webhooks/" in lower || "discordapp.com/api/webhooks/" in lower -> Service.DISCORD
            "hooks.slack.com/services/" in lower -> Service.SLACK
            else -> Service.GENERIC
        }
    }

    private fun eventPrefix(event: String): String =
        when (event) {
            "quest_created" -> "\uD83C\uDD95 新しいクエスト"
            "quest_verified" -> "\u2705 クエスト達成"
            else -> event
        }

    private fun buildDiscordPayload(
        event: String,
        quest: Quest,
    ): DiscordPayload {
        val prefix = eventPrefix(event)
        return DiscordPayload(
            embeds =
                listOf(
                    DiscordEmbed(
                        title = "$prefix: ${quest.title}",
                        description = quest.description,
                        color = DISCORD_EMBED_COLOR,
                        fields =
                            listOf(
                                DiscordField(name = "報酬", value = "${quest.rewardPoints}pt", inline = true),
                                DiscordField(name = "依頼者", value = quest.creatorName, inline = true),
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
        return SlackPayload(
            text = "$prefix: ${quest.title}\n${quest.description}\n報酬: ${quest.rewardPoints}pt | 依頼者: ${quest.creatorName}",
        )
    }

    private fun buildGenericPayload(
        event: String,
        quest: Quest,
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
            timestamp = Instant.now().toString(),
        )

    private enum class Service { DISCORD, SLACK, GENERIC }

    /** Discord embed カラー (primary: #E8844A) */
    private companion object {
        const val DISCORD_EMBED_COLOR = 0xE8844A
    }
}

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
