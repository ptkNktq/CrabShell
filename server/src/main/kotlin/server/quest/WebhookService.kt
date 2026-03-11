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

    /** fire-and-forget でクエストイベントを送信 */
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

    /**
     * fire-and-forget で汎用イベントを送信。
     * [content] は embed 外のテキスト（Discord ではメンション可能）。
     */
    fun notify(
        event: String,
        content: String,
        title: String,
        description: String,
    ) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank() || event !in settings.events) return@launch

                val payload = buildSimplePayload(settings.url, event, content, title, description)

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Webhook delivery failed for event=$event: ${e.message}")
            }
        }
    }

    /** 指定 URL に直接送信（共有 webhook 設定を使わない） */
    fun sendTo(
        url: String,
        content: String,
        title: String,
        description: String,
    ) {
        scope.launch {
            try {
                val payload = buildSimplePayload(url, "", content, title, description)
                client.post(url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Webhook delivery to $url failed: ${e.message}")
            }
        }
    }

    /** 指定 URL に直接送信（Discord のみリンクボタン付き） */
    fun sendToWithButton(
        url: String,
        content: String,
        title: String,
        description: String,
        buttonLabel: String,
        buttonUrl: String,
    ) {
        scope.launch {
            try {
                val payload =
                    if (detectService(url) == Service.DISCORD) {
                        json.encodeToString(
                            DiscordPayload(
                                content = content.ifBlank { null },
                                embeds =
                                    listOf(
                                        DiscordEmbed(
                                            title = title,
                                            description = description,
                                            color = DISCORD_EMBED_COLOR,
                                            fields = emptyList(),
                                        ),
                                    ),
                                components =
                                    listOf(
                                        DiscordActionRow(
                                            components =
                                                listOf(
                                                    DiscordButton(
                                                        label = buttonLabel,
                                                        url = buttonUrl,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        )
                    } else {
                        buildSimplePayload(url, "", content, title, description)
                    }
                client.post(url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Webhook delivery to $url failed: ${e.message}")
            }
        }
    }

    /** URL パターンからサービスを判別し JSON 文字列を生成 */
    internal fun buildPayload(
        url: String,
        event: String,
        quest: Quest,
        timestamp: String = Instant.now().toString(),
    ): String =
        when (detectService(url)) {
            Service.DISCORD -> json.encodeToString(buildDiscordPayload(event, quest))
            Service.SLACK -> json.encodeToString(buildSlackPayload(event, quest))
            Service.GENERIC -> json.encodeToString(buildGenericPayload(event, quest, timestamp))
        }

    internal fun buildSimplePayload(
        url: String,
        event: String,
        content: String,
        title: String,
        description: String,
        timestamp: String = Instant.now().toString(),
    ): String =
        when (detectService(url)) {
            Service.DISCORD ->
                json.encodeToString(
                    DiscordPayload(
                        content = content.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordEmbed(
                                    title = title,
                                    description = description,
                                    color = DISCORD_EMBED_COLOR,
                                    fields = emptyList(),
                                ),
                            ),
                    ),
                )
            Service.SLACK -> {
                val text =
                    if (content.isBlank()) {
                        "$title\n$description"
                    } else {
                        "$content\n$title\n$description"
                    }
                json.encodeToString(SlackPayload(text = text))
            }
            Service.GENERIC ->
                json.encodeToString(
                    GenericSimplePayload(
                        event = event,
                        content = content,
                        title = title,
                        description = description,
                        timestamp = timestamp,
                    ),
                )
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

    private enum class Service { DISCORD, SLACK, GENERIC }

    /** Discord embed カラー (primary: #E8844A) */
    private companion object {
        const val DISCORD_EMBED_COLOR = 0xE8844A
    }
}

// --- Discord ペイロード ---

@Serializable
private data class DiscordPayload(
    val content: String? = null,
    val embeds: List<DiscordEmbed>,
    val components: List<DiscordActionRow>? = null,
)

/** Discord Action Row (type=1) */
@Serializable
private data class DiscordActionRow(
    val type: Int = 1,
    val components: List<DiscordButton>,
)

/** Discord Link Button (type=2, style=5) */
@Serializable
private data class DiscordButton(
    val type: Int = 2,
    val style: Int = 5,
    val label: String,
    val url: String,
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

@Serializable
private data class GenericSimplePayload(
    val event: String,
    val content: String,
    val title: String,
    val description: String,
    val timestamp: String,
)
