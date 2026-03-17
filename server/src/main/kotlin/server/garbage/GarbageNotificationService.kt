package server.garbage

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.GarbageType
import model.resolveGarbageTypes
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.detectWebhookService
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val logger = LoggerFactory.getLogger("GarbageNotificationService")
private val JST = ZoneId.of("Asia/Tokyo")

class GarbageNotificationService(
    private val garbageRepository: GarbageRepository,
    private val client: HttpClient = HttpClient(),
) {
    private val json = Json

    // 通知済みの日付（JST カレンダー日付）
    internal var notifiedDate: String? = null

    suspend fun runPollingLoop() {
        while (true) {
            try {
                checkAndNotify()
            } catch (e: Exception) {
                logger.warn("Garbage notification check failed: ${e.message}")
            }
            delay(60_000L)
        }
    }

    internal suspend fun checkAndNotify(now: Instant = Instant.now()) {
        val settings = garbageRepository.getNotificationSettings()
        if (!settings.enabled || settings.webhookUrl.isBlank()) return

        val jstNow = now.atZone(JST)
        val today = jstNow.toLocalDate().toString()
        val currentTime = jstNow.toLocalTime()
        val notifyTime = parseTime(settings.notifyTime) ?: return

        // 日付が変わったらリセット
        if (notifiedDate != null && notifiedDate != today) {
            notifiedDate = null
        }

        // 既に今日通知済み
        if (notifiedDate == today) return

        // 通知時刻に達していない
        if (currentTime < notifyTime) return

        // 翌日のゴミ種を解決
        val tomorrow = jstNow.plusDays(1)
        val dayOfWeek = tomorrow.dayOfWeek.value % 7 // Monday=1..Sunday=7 → 0=Sun..6=Sat
        val weekOfMonth = weekOfMonth(tomorrow)

        val schedules = garbageRepository.getSchedules()
        val garbageTypes = resolveGarbageTypes(schedules, dayOfWeek, weekOfMonth)

        // ゴミなしの日はスキップ
        if (garbageTypes.isEmpty()) {
            notifiedDate = today
            return
        }

        sendWebhook(
            url = settings.webhookUrl,
            prefix = settings.prefix,
            garbageTypes = garbageTypes,
        )
        notifiedDate = today
    }

    internal suspend fun sendWebhook(
        url: String,
        prefix: String,
        garbageTypes: List<GarbageType>,
    ) {
        val payload = buildPayload(url, prefix, garbageTypes)
        try {
            client.post(url) {
                setBody(TextContent(payload, ContentType.Application.Json))
            }
        } catch (e: Exception) {
            logger.warn("Garbage notification webhook failed: ${e.javaClass.simpleName}")
        }
    }

    internal fun buildPayload(
        url: String,
        prefix: String,
        garbageTypes: List<GarbageType>,
        dashboardUrl: String? = appUrl,
    ): String {
        val typeLabels = garbageTypes.joinToString("・") { garbageTypeLabel(it) }
        val message = "明日は${typeLabels}の日です"
        return when (detectWebhookService(url)) {
            WebhookServiceType.DISCORD -> {
                json.encodeToString(
                    DiscordGarbagePayload(
                        content = prefix.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordGarbageEmbed(
                                    title = "ゴミ出しリマインダー",
                                    description = message,
                                    color = DISCORD_EMBED_COLOR,
                                    url = dashboardUrl,
                                ),
                            ),
                    ),
                )
            }
            WebhookServiceType.SLACK -> {
                val text = if (prefix.isBlank()) message else "$prefix $message"
                val withLink =
                    if (dashboardUrl != null) "$text\n<$dashboardUrl|ダッシュボードを開く>" else text
                json.encodeToString(SlackGarbagePayload(text = withLink))
            }
            WebhookServiceType.GENERIC -> {
                json.encodeToString(
                    GenericGarbagePayload(
                        event = "garbage_reminder",
                        garbageTypes = garbageTypes.map { it.name },
                        message = message,
                        dashboardUrl = dashboardUrl,
                    ),
                )
            }
        }
    }

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]

        internal fun parseTime(timeStr: String): LocalTime? =
            try {
                LocalTime.parse(timeStr)
            } catch (_: Exception) {
                null
            }

        /** ZonedDateTime から月内の週番号を算出（1始まり） */
        internal fun weekOfMonth(date: ZonedDateTime): Int {
            val dayOfMonth = date.dayOfMonth
            return (dayOfMonth - 1) / 7 + 1
        }
    }
}

private fun garbageTypeLabel(type: GarbageType): String =
    when (type) {
        GarbageType.BURNABLE -> "可燃ゴミ"
        GarbageType.NON_BURNABLE -> "不燃ゴミ"
        GarbageType.RECYCLABLE -> "資源ゴミ"
    }

// --- Discord ペイロード ---

@Serializable
internal data class DiscordGarbagePayload(
    val content: String? = null,
    val embeds: List<DiscordGarbageEmbed>,
)

@Serializable
internal data class DiscordGarbageEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val url: String? = null,
)

// --- Slack ペイロード ---

@Serializable
internal data class SlackGarbagePayload(
    val text: String,
)

// --- 汎用ペイロード ---

@Serializable
internal data class GenericGarbagePayload(
    val event: String,
    val garbageTypes: List<String>,
    val message: String,
    val dashboardUrl: String? = null,
)
