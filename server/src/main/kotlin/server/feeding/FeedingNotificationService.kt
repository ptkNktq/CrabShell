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

private val JST = ZoneId.of("Asia/Tokyo")

/** 給餌通知のフェーズ。SCHEDULED は予定時刻の告知、REMINDER は遅延後の催促。 */
enum class FeedingNotificationPhase { SCHEDULED, REMINDER }

class FeedingNotificationService(
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
    private val logger = LoggerFactory.getLogger(FeedingNotificationService::class.java)
    private val json = Json

    // 単一コルーチンからのみアクセスされるため通常の HashMap で十分
    // key: "petId:date", value: 通知済みの (MealTime, Phase) セット
    internal val notifiedMap = HashMap<String, MutableSet<Pair<MealTime, FeedingNotificationPhase>>>()

    /** Application のコルーチンスコープから呼び出す。キャンセルで停止する。 */
    suspend fun runPollingLoop() {
        while (true) {
            try {
                checkAndNotify()
            } catch (e: Exception) {
                logger.warn("Feeding reminder check failed", e)
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

                // 既に給餌済み
                val feeding = log.feedings[mealTime]
                if (feeding != null && feeding.done) continue

                val key = "${pet.id}:$feedingDate"
                val notified = notifiedMap.getOrPut(key) { mutableSetOf() }
                val scheduledKey = mealTime to FeedingNotificationPhase.SCHEDULED
                val reminderKey = mealTime to FeedingNotificationPhase.REMINDER

                when {
                    // REMINDER 時刻を過ぎている: REMINDER を送信し、SCHEDULED もマーク
                    // （取りこぼし時の遅延通知や重複回避のため）
                    isPastTime(currentTime, reminderTime) && reminderKey !in notified -> {
                        sendWebhook(
                            url = settings.reminderWebhookUrl,
                            prefix = settings.reminderPrefix,
                            phase = FeedingNotificationPhase.REMINDER,
                            petId = pet.id,
                            petName = pet.name,
                            mealTime = mealTime,
                            scheduledTime = scheduledTimeStr,
                        )
                        notified += reminderKey
                        notified += scheduledKey
                    }
                    // SCHEDULED 時刻を過ぎているが REMINDER 時刻前: SCHEDULED のみ送信
                    isPastTime(currentTime, scheduledTime) && scheduledKey !in notified -> {
                        sendWebhook(
                            url = settings.reminderWebhookUrl,
                            prefix = settings.reminderPrefix,
                            phase = FeedingNotificationPhase.SCHEDULED,
                            petId = pet.id,
                            petName = pet.name,
                            mealTime = mealTime,
                            scheduledTime = scheduledTimeStr,
                        )
                        notified += scheduledKey
                    }
                }
            }
        }
    }

    /** テスト送信: 保存済み設定を使って最初のペット・最初の食事で指定 phase を即時送信 */
    suspend fun sendTestNotification(phase: FeedingNotificationPhase) {
        val settings = feedingSettingsRepository.getSettings()
        require(settings.reminderWebhookUrl.isNotBlank()) { "Webhook URL が設定されていません" }
        val pet = petRepository.getPets().firstOrNull() ?: error("ペットが登録されていません")
        val mealTime = settings.mealOrder.firstOrNull() ?: MealTime.MORNING
        val scheduledTime = settings.mealTimes[mealTime] ?: "00:00"
        sendWebhook(
            url = settings.reminderWebhookUrl,
            prefix = settings.reminderPrefix,
            phase = phase,
            petId = pet.id,
            petName = pet.name,
            mealTime = mealTime,
            scheduledTime = scheduledTime,
        )
    }

    internal suspend fun sendWebhook(
        url: String,
        prefix: String,
        phase: FeedingNotificationPhase,
        petId: String,
        petName: String,
        mealTime: MealTime,
        scheduledTime: String,
    ) {
        val mealLabel = mealTimeLabel(mealTime)
        val payload = buildPayload(url, prefix, phase, petId, petName, mealLabel, scheduledTime)
        try {
            client.post(url) {
                setBody(TextContent(payload, ContentType.Application.Json))
            }
        } catch (e: Exception) {
            logger.warn("Feeding notification webhook failed (phase=$phase)", e)
        }
    }

    internal fun buildPayload(
        url: String,
        prefix: String,
        phase: FeedingNotificationPhase,
        petId: String,
        petName: String,
        mealLabel: String,
        scheduledTime: String,
        feedingPageUrl: String? = appUrl?.let { "${it.trimEnd('/')}/feeding" },
    ): String {
        val title =
            when (phase) {
                FeedingNotificationPhase.SCHEDULED -> "給餌時間"
                FeedingNotificationPhase.REMINDER -> "給餌リマインダー"
            }
        val message =
            when (phase) {
                FeedingNotificationPhase.SCHEDULED ->
                    "${petName}の${mealLabel}ごはんの時間です（予定: $scheduledTime）"
                FeedingNotificationPhase.REMINDER ->
                    "${petName}の${mealLabel}ごはん（予定: $scheduledTime）がまだ記録されていません"
            }
        val event =
            when (phase) {
                FeedingNotificationPhase.SCHEDULED -> "feeding_scheduled"
                FeedingNotificationPhase.REMINDER -> "feeding_reminder"
            }
        return when (detectWebhookService(url)) {
            WebhookServiceType.DISCORD -> {
                val discord =
                    DiscordNotificationPayload(
                        content = prefix.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordNotificationEmbed(
                                    title = title,
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
                json.encodeToString(SlackNotificationPayload(text = withLink))
            }
            WebhookServiceType.GENERIC -> {
                json.encodeToString(
                    GenericNotificationPayload(
                        event = event,
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
         * 5時境界を考慮して、現在時刻が target 時刻を過ぎているか判定。
         * 朝5時が1日の開始。target が 5:00 未満なら翌日扱い。
         * SCHEDULED / REMINDER 双方の判定で使う。
         */
        internal fun isPastTime(
            currentTime: LocalTime,
            target: LocalTime,
        ): Boolean {
            val dayStart = LocalTime.of(5, 0)
            return if (target >= dayStart) {
                // target が 5:00 以降: 現在時刻も 5:00 以降で、かつ過ぎている
                currentTime >= dayStart && currentTime >= target
            } else {
                // target が 5:00 未満（深夜帯）: 現在時刻が 5:00 前で過ぎている
                currentTime < dayStart && currentTime >= target
            }
        }
    }
}

// --- Discord ペイロード ---

@Serializable
internal data class DiscordNotificationPayload(
    val content: String? = null,
    val embeds: List<DiscordNotificationEmbed>,
)

@Serializable
internal data class DiscordNotificationEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val url: String? = null,
)

// --- Slack ペイロード ---

@Serializable
internal data class SlackNotificationPayload(
    val text: String,
)

// --- 汎用ペイロード ---

@Serializable
internal data class GenericNotificationPayload(
    val event: String,
    val prefix: String? = null,
    val petId: String,
    val petName: String,
    val mealTime: String,
    val scheduledTime: String,
    val message: String,
    val feedingPageUrl: String? = null,
)
