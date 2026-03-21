package server.feeding

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.MealTime
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import server.pet.PetRepository
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.detectWebhookService
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val logger = LoggerFactory.getLogger("FeedingReminderService")
private val JST = ZoneId.of("Asia/Tokyo")

class FeedingReminderService(
    private val feedingRepository: FeedingRepository,
    private val feedingSettingsRepository: FeedingSettingsRepository,
    private val petRepository: PetRepository,
    private val client: HttpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
        },
) {
    private val json = Json

    // 単一コルーチンからのみアクセスされるため通常の HashMap で十分
    // key: "petId:date", value: 通知済みの MealTime セット
    internal val notifiedMap = HashMap<String, MutableSet<MealTime>>()

    /** Application のコルーチンスコープから呼び出す。キャンセルで停止する。 */
    suspend fun runPollingLoop() {
        while (true) {
            try {
                checkAndNotify()
            } catch (e: Exception) {
                logger.warn("Feeding reminder check failed: ${e.message}")
            }
            delay(60_000L)
        }
    }

    internal suspend fun checkAndNotify(now: Instant = Instant.now()) {
        val settings = feedingSettingsRepository.getSettings()
        if (!settings.reminderEnabled || settings.reminderWebhookUrl.isBlank()) return

        val jstNow = now.atZone(JST)
        val feedingDate = feedingDate(jstNow)
        val currentTime = jstNow.toLocalTime()

        // 現在の feedingDate 以外のエントリを全て削除（数日停止後の再起動にも対応）
        notifiedMap.keys.removeAll { !it.endsWith(":$feedingDate") }

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
                    petId = pet.id,
                    petName = pet.name,
                    mealTime = mealTime,
                    scheduledTime = scheduledTimeStr,
                )
                notified.add(mealTime)
            }
        }
    }

    /** テスト送信: 保存済み設定を使って最初のペット・最初の食事でリマインダーを即時送信 */
    suspend fun sendTestReminder() {
        val settings = feedingSettingsRepository.getSettings()
        require(settings.reminderWebhookUrl.isNotBlank()) { "Webhook URL が設定されていません" }
        val pet = petRepository.getPets().firstOrNull() ?: error("ペットが登録されていません")
        val mealTime = settings.mealOrder.firstOrNull() ?: MealTime.MORNING
        val scheduledTime = settings.mealTimes[mealTime] ?: "00:00"
        sendWebhook(
            url = settings.reminderWebhookUrl,
            prefix = settings.reminderPrefix,
            petId = pet.id,
            petName = pet.name,
            mealTime = mealTime,
            scheduledTime = scheduledTime,
        )
    }

    internal suspend fun sendWebhook(
        url: String,
        prefix: String,
        petId: String,
        petName: String,
        mealTime: MealTime,
        scheduledTime: String,
    ) {
        val mealLabel = mealTimeLabel(mealTime)
        val payload = buildPayload(url, prefix, petId, petName, mealLabel, scheduledTime)
        try {
            client.post(url) {
                setBody(TextContent(payload, ContentType.Application.Json))
            }
        } catch (e: Exception) {
            logger.warn("Feeding reminder webhook failed: ${e.message}")
        }
    }

    internal fun buildPayload(
        url: String,
        prefix: String,
        petId: String,
        petName: String,
        mealLabel: String,
        scheduledTime: String,
        feedingPageUrl: String? = appUrl?.let { "${it.trimEnd('/')}/feeding" },
    ): String {
        val message = "${petName}の${mealLabel}ごはん（予定: $scheduledTime）がまだ記録されていません"
        return when (detectWebhookService(url)) {
            WebhookServiceType.DISCORD -> {
                val discord =
                    DiscordReminderPayload(
                        content = prefix.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordReminderEmbed(
                                    title = "給餌リマインダー",
                                    description = message,
                                    color = DISCORD_EMBED_COLOR,
                                    url = feedingPageUrl,
                                ),
                            ),
                    )
                json.encodeToString(discord)
            }
            WebhookServiceType.SLACK -> {
                val text = if (prefix.isBlank()) message else "$prefix $message"
                val withLink =
                    if (feedingPageUrl != null) "$text\n<$feedingPageUrl|ごはんページを開く>" else text
                json.encodeToString(SlackReminderPayload(text = withLink))
            }
            WebhookServiceType.GENERIC -> {
                json.encodeToString(
                    GenericReminderPayload(
                        event = "feeding_reminder",
                        prefix = prefix.ifBlank { null },
                        petId = petId,
                        petName = petName,
                        mealTime = mealLabel,
                        scheduledTime = scheduledTime,
                        message = message,
                        feedingPageUrl = feedingPageUrl,
                    ),
                )
            }
        }
    }

    private fun mealTimeLabel(mealTime: MealTime): String =
        when (mealTime) {
            MealTime.MORNING -> "朝"
            MealTime.LUNCH -> "昼"
            MealTime.EVENING -> "晩"
        }

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]

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
    val url: String? = null,
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
    val prefix: String? = null,
    val petId: String,
    val petName: String,
    val mealTime: String,
    val scheduledTime: String,
    val message: String,
    val feedingPageUrl: String? = null,
)
