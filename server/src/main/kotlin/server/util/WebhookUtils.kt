package server.util

/** Webhook 送信先サービスの種別 */
enum class WebhookServiceType { DISCORD, SLACK, GENERIC }

/** Discord embed カラー (primary: #E8844A) */
const val DISCORD_EMBED_COLOR = 0xE8844A

/** URL パターンから Webhook サービスを判別する */
fun detectWebhookService(url: String): WebhookServiceType {
    val lower = url.lowercase()
    return when {
        "discord.com/api/webhooks/" in lower || "discordapp.com/api/webhooks/" in lower ->
            WebhookServiceType.DISCORD
        "hooks.slack.com/services/" in lower -> WebhookServiceType.SLACK
        else -> WebhookServiceType.GENERIC
    }
}
