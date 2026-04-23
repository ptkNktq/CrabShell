package server.money

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
import model.MoneyWebhookSettings
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.await
import server.util.detectWebhookService

private val logger = LoggerFactory.getLogger("MoneyWebhookService")

class MoneyWebhookService(
    private val firestore: Firestore,
    private val client: HttpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
        },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val moneySettingsDoc get() = firestore.collection("settings").document("money")
    private val scope = CoroutineScope(dispatcher)

    private val json = Json

    @Suppress("UNCHECKED_CAST")
    suspend fun getSettings(): MoneyWebhookSettings {
        val doc = moneySettingsDoc.get().await()
        if (!doc.exists()) return MoneyWebhookSettings()
        val webhook = (doc.data?.get("webhook") as? Map<String, Any?>) ?: return MoneyWebhookSettings()
        return MoneyWebhookSettings(
            url = webhook["url"] as? String ?: "",
            enabled = webhook["enabled"] as? Boolean ?: false,
            message = webhook["message"] as? String ?: "",
        )
    }

    suspend fun updateSettings(settings: MoneyWebhookSettings) {
        moneySettingsDoc
            .set(
                mapOf(
                    "webhook" to
                        mapOf(
                            "url" to settings.url,
                            "enabled" to settings.enabled,
                            "message" to settings.message,
                        ),
                ),
                SetOptions.merge(),
            ).await()
    }

    /** 月次ステータス確定時の通知を fire-and-forget で送信 */
    fun notifyConfirmed(yearMonth: String) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank()) return@launch

                val payload = buildPayload(settings.url, settings.message, yearMonth)

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Money webhook delivery failed for yearMonth=$yearMonth", e)
            }
        }
    }

    internal fun buildPayload(
        url: String,
        message: String,
        yearMonth: String,
        dashboardUrl: String? = appUrl,
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

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]

        /** "YYYY-MM" を "YYYY年M月" 表記に整形。パース失敗時は入力をそのまま返す。 */
        internal fun formatYearMonth(yearMonth: String): String {
            val parts = yearMonth.split("-")
            if (parts.size != 2) return yearMonth
            val year = parts[0].toIntOrNull() ?: return yearMonth
            val month = parts[1].toIntOrNull() ?: return yearMonth
            return "${year}年${month}月"
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
