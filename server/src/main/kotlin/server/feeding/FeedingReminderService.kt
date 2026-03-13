package server.feeding

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.MealTime
import org.slf4j.LoggerFactory
import server.pet.PetRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("FeedingReminderService")
private val JST = ZoneId.of("Asia/Tokyo")

class FeedingReminderService(
    private val feedingRepository: FeedingRepository,
    private val feedingSettingsRepository: FeedingSettingsRepository,
    private val petRepository: PetRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal var clientFactory: () -> HttpClient = { HttpClient() },
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val json = Json

    // key: "petId:date", value: 通知済みの MealTime セット
    internal val notifiedMap = ConcurrentHashMap<String, MutableSet<MealTime>>()
    private var lastDate: String? = null

    fun start() {
        scope.launch {
            while (true) {
                try {
                    checkAndNotify()
                } catch (e: Exception) {
                    logger.warn("Feeding reminder check failed: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }

    internal suspend fun checkAndNotify(now: Instant = Instant.now()) {
        val settings = feedingSettingsRepository.getSettings()
        if (!settings.reminderEnabled || settings.reminderWebhookUrl.isBlank()) return

        val jstNow = now.atZone(JST)
        val feedingDate = feedingDate(jstNow)
        val currentTime = jstNow.toLocalTime()

        // 日付変更時にクリーンアップ
        if (lastDate != null && lastDate != feedingDate) {
            notifiedMap.keys.removeAll { it.endsWith(":$lastDate") }
        }
        lastDate = feedingDate

        val pets = petRepository.getPets()

        for (pet in pets) {
            val log = feedingRepository.getFeedingLog(pet.id, feedingDate)

            for (mealTime in MealTime.entries) {
                val scheduledTimeStr = settings.mealTimes[mealTime] ?: continue
                val scheduledTime = parseTime(scheduledTimeStr) ?: continue
                val reminderTime = scheduledTime.plusMinutes(settings.reminderDelayMinutes.toLong())

                // 現在時刻がリマインダー時刻を過ぎているか（5時境界を考慮）
                if (!isPastReminderTime(currentTime, reminderTime)) continue

                // 既に給餌済み
                val feeding = log.feedings[mealTime]
                if (feeding != null && feeding.done) continue

                // 既に通知済み
                val key = "${pet.id}:$feedingDate"
                val notified = notifiedMap.getOrPut(key) { mutableSetOf() }
                if (mealTime in notified) continue

                // 通知送信
                sendWebhook(
                    url = settings.reminderWebhookUrl,
                    prefix = settings.reminderPrefix,
                    petName = pet.name,
                    mealTime = mealTime,
                    scheduledTime = scheduledTimeStr,
                )
                notified.add(mealTime)
            }
        }
    }

    internal suspend fun sendWebhook(
        url: String,
        prefix: String,
        petName: String,
        mealTime: MealTime,
        scheduledTime: String,
    ) {
        val mealLabel = mealTimeLabel(mealTime)
        val payload = buildPayload(url, prefix, petName, mealLabel, scheduledTime)
        try {
            val client = clientFactory()
            client.use {
                it.post(url) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
            }
        } catch (e: Exception) {
            logger.warn("Feeding reminder webhook failed: ${e.message}")
        }
    }

    internal fun buildPayload(
        url: String,
        prefix: String,
        petName: String,
        mealLabel: String,
        scheduledTime: String,
    ): String {
        val message = "${petName}の${mealLabel}ごはん（予定: $scheduledTime）がまだ記録されていません"
        return when (detectService(url)) {
            Service.DISCORD -> {
                val discord =
                    DiscordReminderPayload(
                        content = prefix.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordReminderEmbed(
                                    title = "給餌リマインダー",
                                    description = message,
                                    color = DISCORD_EMBED_COLOR,
                                    fields =
                                        listOf(
                                            DiscordReminderField(name = "ペット", value = petName, inline = true),
                                            DiscordReminderField(name = "食事", value = mealLabel, inline = true),
                                            DiscordReminderField(name = "予定時刻", value = scheduledTime, inline = true),
                                        ),
                                ),
                            ),
                    )
                json.encodeToString(discord)
            }
            Service.SLACK -> {
                val text = if (prefix.isBlank()) message else "$prefix $message"
                json.encodeToString(SlackReminderPayload(text = text))
            }
            Service.GENERIC -> {
                json.encodeToString(
                    GenericReminderPayload(
                        event = "feeding_reminder",
                        petName = petName,
                        mealTime = mealLabel,
                        scheduledTime = scheduledTime,
                        message = message,
                    ),
                )
            }
        }
    }

    private fun detectService(url: String): Service {
        val lower = url.lowercase()
        return when {
            "discord.com/api/webhooks/" in lower || "discordapp.com/api/webhooks/" in lower -> Service.DISCORD
            "hooks.slack.com/services/" in lower -> Service.SLACK
            else -> Service.GENERIC
        }
    }

    private fun mealTimeLabel(mealTime: MealTime): String =
        when (mealTime) {
            MealTime.MORNING -> "朝"
            MealTime.LUNCH -> "昼"
            MealTime.EVENING -> "晩"
        }

    private enum class Service { DISCORD, SLACK, GENERIC }

    companion object {
        private const val DISCORD_EMBED_COLOR = 0xE8844A

        /** JST 5:00 AM を日付境界とする給餌日付を算出 */
        internal fun feedingDate(jstNow: ZonedDateTime): String {
            val adjusted = if (jstNow.hour < 5) jstNow.minusDays(1) else jstNow
            return adjusted.toLocalDate().toString()
        }

        internal fun parseTime(timeStr: String): LocalTime? =
            try {
                LocalTime.parse(timeStr)
            } catch (_: Exception) {
                null
            }

        /**
         * 5時境界を考慮して、現在時刻がリマインダー時刻を過ぎているか判定。
         * 朝5時が1日の開始。reminderTime が 5:00 未満なら翌日扱い。
         */
        internal fun isPastReminderTime(
            currentTime: LocalTime,
            reminderTime: LocalTime,
        ): Boolean {
            val dayStart = LocalTime.of(5, 0)
            return if (reminderTime >= dayStart) {
                // リマインダーが 5:00 以降: 現在時刻も 5:00 以降で、かつ過ぎている
                currentTime >= dayStart && currentTime >= reminderTime
            } else {
                // リマインダーが 5:00 未満（深夜帯）: 現在時刻が 5:00 前で過ぎている、または既に 5:00 以降
                currentTime < dayStart && currentTime >= reminderTime
            }
        }
    }
}

// --- Discord ペイロード ---

@Serializable
internal data class DiscordReminderPayload(
    val content: String? = null,
    val embeds: List<DiscordReminderEmbed>,
)

@Serializable
internal data class DiscordReminderEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val fields: List<DiscordReminderField>,
)

@Serializable
internal data class DiscordReminderField(
    val name: String,
    val value: String,
    val inline: Boolean = false,
)

// --- Slack ペイロード ---

@Serializable
internal data class SlackReminderPayload(
    val text: String,
)

// --- 汎用ペイロード ---

@Serializable
internal data class GenericReminderPayload(
    val event: String,
    val petName: String,
    val mealTime: String,
    val scheduledTime: String,
    val message: String,
)
