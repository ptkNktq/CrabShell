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
import model.PaymentWebhookSettings
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.await
import server.util.detectWebhookService

class PaymentWebhookService(
    private val firestore: Firestore,
    private val client: HttpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
        },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(PaymentWebhookService::class.java)
    private val paymentSettingsDoc get() = firestore.collection("settings").document("payment")
    private val scope = CoroutineScope(dispatcher)

    private val json = Json

    @Suppress("UNCHECKED_CAST")
    suspend fun getSettings(): PaymentWebhookSettings {
        val doc = paymentSettingsDoc.get().await()
        if (!doc.exists()) return PaymentWebhookSettings()
        val webhook = (doc.data?.get("webhook") as? Map<String, Any?>) ?: return PaymentWebhookSettings()
        return PaymentWebhookSettings(
            url = webhook["url"] as? String ?: "",
            enabled = webhook["enabled"] as? Boolean ?: false,
            message = webhook["message"] as? String ?: "",
        )
    }

    suspend fun updateSettings(settings: PaymentWebhookSettings) {
        paymentSettingsDoc
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

    /** 入金通知を fire-and-forget で送信 */
    fun notifyPayment(
        yearMonth: String,
        payerName: String,
        amount: Long,
    ) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank()) return@launch

                val payload =
                    buildPayload(
                        url = settings.url,
                        message = settings.message,
                        yearMonth = yearMonth,
                        payerName = payerName,
                        amount = amount,
                    )

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Payment webhook delivery failed for yearMonth=$yearMonth payer=$payerName", e)
            }
        }
    }

    internal fun buildPayload(
        url: String,
        message: String,
        yearMonth: String,
        payerName: String,
        amount: Long,
        dashboardUrl: String? = appUrl,
    ): String {
        val formattedYearMonth = formatYearMonth(yearMonth)
        val formattedAmount = formatAmount(amount)
        val description = "$formattedYearMonth に $payerName が $formattedAmount を支払いました"
        return when (detectWebhookService(url)) {
            WebhookServiceType.DISCORD -> {
                val fields =
                    listOf(
                        DiscordPaymentField(name = "支払者", value = payerName, inline = true),
                        DiscordPaymentField(name = "金額", value = formattedAmount, inline = true),
                        DiscordPaymentField(name = "対象月", value = formattedYearMonth, inline = true),
                    )
                json.encodeToString(
                    DiscordPaymentPayload(
                        content = message.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordPaymentEmbed(
                                    title = "入金あり",
                                    description = description,
                                    color = DISCORD_EMBED_COLOR,
                                    url = dashboardUrl,
                                    fields = fields,
                                ),
                            ),
                    ),
                )
            }
            WebhookServiceType.SLACK -> {
                val base = if (message.isBlank()) description else "$message\n$description"
                val withLink =
                    if (dashboardUrl != null) "$base\n<$dashboardUrl|ダッシュボードを開く>" else base
                json.encodeToString(SlackPaymentPayload(text = withLink))
            }
            WebhookServiceType.GENERIC -> {
                json.encodeToString(
                    GenericPaymentPayload(
                        event = "payment_recorded",
                        yearMonth = yearMonth,
                        payerName = payerName,
                        amount = amount,
                        message = message,
                        dashboardUrl = dashboardUrl,
                    ),
                )
            }
        }
    }

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]

        /** "YYYY-MM" を "YYYY年MM月" 表記（月は 0 埋め 2 桁）に整形。パース失敗時は入力をそのまま返す。 */
        internal fun formatYearMonth(yearMonth: String): String {
            val parts = yearMonth.split("-")
            if (parts.size != 2) return yearMonth
            val year = parts[0].toIntOrNull() ?: return yearMonth
            val month = parts[1].toIntOrNull() ?: return yearMonth
            return "%d年%02d月".format(year, month)
        }

        /** 金額を 3 桁区切り + 円付きで整形（例: 12345 → "12,345 円"）。負値は支払い取消等の意味合いで先頭に符号付き。 */
        internal fun formatAmount(amount: Long): String = "%,d 円".format(amount)
    }
}

// --- Discord ペイロード ---

@Serializable
internal data class DiscordPaymentPayload(
    val content: String? = null,
    val embeds: List<DiscordPaymentEmbed>,
)

@Serializable
internal data class DiscordPaymentEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val url: String? = null,
    val fields: List<DiscordPaymentField> = emptyList(),
)

@Serializable
internal data class DiscordPaymentField(
    val name: String,
    val value: String,
    val inline: Boolean = false,
)

// --- Slack ペイロード ---

@Serializable
internal data class SlackPaymentPayload(
    val text: String,
)

// --- 汎用ペイロード ---

@Serializable
internal data class GenericPaymentPayload(
    val event: String,
    val yearMonth: String,
    val payerName: String,
    val amount: Long,
    val message: String,
    val dashboardUrl: String? = null,
)
