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
import model.PaymentWebhookSettings
import server.config.EnvConfig
import server.util.AbstractWebhookService
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.defaultHttpClient
import server.util.detectWebhookService
import server.util.formatAmount
import server.util.formatYearMonth
import server.util.sanitizeForDiscord
import server.util.sanitizeForSlack

class PaymentWebhookService(
    firestore: Firestore,
    client: HttpClient = defaultHttpClient(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AbstractWebhookService<PaymentWebhookSettings>(firestore, "payment", client, dispatcher) {
    override fun defaultSettings() = PaymentWebhookSettings()

    override fun fromWebhookMap(map: Map<String, Any?>) =
        PaymentWebhookSettings(
            url = map["url"] as? String ?: "",
            enabled = map["enabled"] as? Boolean ?: false,
            message = map["message"] as? String ?: "",
        )

    override fun toWebhookMap(settings: PaymentWebhookSettings) =
        mapOf(
            "url" to settings.url,
            "enabled" to settings.enabled,
            "message" to settings.message,
        )

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
                    buildPaymentPayload(
                        url = settings.url,
                        message = settings.message,
                        yearMonth = yearMonth,
                        payerName = payerName,
                        amount = amount,
                        dashboardUrl = appUrl,
                    )

                client.post(settings.url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            } catch (e: Exception) {
                logger.warn("Payment webhook delivery failed for yearMonth=$yearMonth payer=$payerName", e)
            }
        }
    }

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]
    }
}

private val json = Json

/**
 * 入金通知の payload を URL のサービス種別に応じて生成する。
 * Firestore 非依存の純粋関数。`dashboardUrl` は呼び出し側で `APP_URL` を解決して渡す。
 */
internal fun buildPaymentPayload(
    url: String,
    message: String,
    yearMonth: String,
    payerName: String,
    amount: Long,
    dashboardUrl: String?,
): String {
    val formattedYearMonth = formatYearMonth(yearMonth)
    val formattedAmount = formatAmount(amount)
    return when (detectWebhookService(url)) {
        WebhookServiceType.DISCORD -> {
            val safePayerName = sanitizeForDiscord(payerName)
            val description = "$formattedYearMonth に $safePayerName が $formattedAmount を支払いました"
            val fields =
                listOf(
                    DiscordPaymentField(name = "支払者", value = safePayerName, inline = true),
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
            val safePayerName = sanitizeForSlack(payerName)
            val description = "$formattedYearMonth に $safePayerName が $formattedAmount を支払いました"
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
