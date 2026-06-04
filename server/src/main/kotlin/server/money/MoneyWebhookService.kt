package server.money

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
import model.MoneyWebhookSettings
import server.config.EnvConfig
import server.util.AbstractWebhookService
import server.util.AbstractWebhookService.Companion.defaultWebhookClient
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.detectWebhookService
import server.util.formatYearMonth

class MoneyWebhookService(
    firestore: Firestore,
    client: HttpClient = defaultWebhookClient(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AbstractWebhookService<MoneyWebhookSettings>(firestore, "money", client, dispatcher) {
    override fun defaultSettings() = MoneyWebhookSettings()

    override fun fromWebhookMap(map: Map<String, Any?>) =
        MoneyWebhookSettings(
            url = map["url"] as? String ?: "",
            enabled = map["enabled"] as? Boolean ?: false,
            message = map["message"] as? String ?: "",
        )

    override fun toWebhookMap(settings: MoneyWebhookSettings) =
        mapOf(
            "url" to settings.url,
            "enabled" to settings.enabled,
            "message" to settings.message,
        )

    /** 月次ステータス確定時の通知を fire-and-forget で送信 */
    fun notifyConfirmed(yearMonth: String) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank()) return@launch

                val payload = buildMoneyPayload(settings.url, settings.message, yearMonth, appUrl)

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Money webhook delivery failed for yearMonth=$yearMonth", e)
            }
        }
    }

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]
    }
}

private val json = Json

/**
 * 月次ステータス確定通知の payload を URL のサービス種別に応じて生成する。
 * Firestore 非依存の純粋関数。`dashboardUrl` は呼び出し側で `APP_URL` を解決して渡す。
 */
internal fun buildMoneyPayload(
    url: String,
    message: String,
    yearMonth: String,
    dashboardUrl: String?,
): String {
    val description = "${formatYearMonth(yearMonth)} の支払額が確定しました"
    return when (detectWebhookService(url)) {
        WebhookServiceType.DISCORD -> {
            json.encodeToString(
                DiscordMoneyPayload(
                    content = message.ifBlank { null },
                    embeds =
                        listOf(
                            DiscordMoneyEmbed(
                                title = "支払額確定",
                                description = description,
                                color = DISCORD_EMBED_COLOR,
                                url = dashboardUrl,
                            ),
                        ),
                ),
            )
        }
        WebhookServiceType.SLACK -> {
            val text = if (message.isBlank()) description else "$message\n$description"
            val withLink =
                if (dashboardUrl != null) "$text\n<$dashboardUrl|ダッシュボードを開く>" else text
            json.encodeToString(SlackMoneyPayload(text = withLink))
        }
        WebhookServiceType.GENERIC -> {
            json.encodeToString(
                GenericMoneyPayload(
                    event = "money_status_confirmed",
                    yearMonth = yearMonth,
                    message = message,
                    dashboardUrl = dashboardUrl,
                ),
            )
        }
    }
}

// --- Discord ペイロード ---

@Serializable
internal data class DiscordMoneyPayload(
    val content: String? = null,
    val embeds: List<DiscordMoneyEmbed>,
)

@Serializable
internal data class DiscordMoneyEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val url: String? = null,
)

// --- Slack ペイロード ---

@Serializable
internal data class SlackMoneyPayload(
    val text: String,
)

// --- 汎用ペイロード ---

@Serializable
internal data class GenericMoneyPayload(
    val event: String,
    val yearMonth: String,
    val message: String,
    val dashboardUrl: String? = null,
)
