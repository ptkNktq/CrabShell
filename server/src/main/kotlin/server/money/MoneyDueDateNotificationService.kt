package server.money

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
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import server.util.DISCORD_EMBED_COLOR
import server.util.WebhookServiceType
import server.util.detectWebhookService
import server.util.formatAmount
import server.util.sanitizeForDiscord
import server.util.sanitizeForSlack
import java.time.Instant
import java.time.ZoneId

private val JST = ZoneId.of("Asia/Tokyo")

/**
 * 支払期日リマインダー。項目 (MoneyItem) の dueDate の [MoneyDueDateNotificationSettings.daysBefore]
 * 日前、JST [MoneyDueDateNotificationSettings.notifyHour] 時に、その日が期日となる項目一覧を
 * Webhook 通知する。項目は月ごとに保存されているため、対象期日に一致する項目を探すには全月を走査する。
 */
class MoneyDueDateNotificationService(
    private val moneyRepository: MoneyRepository,
    private val client: HttpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
        },
) {
    private val logger = LoggerFactory.getLogger(MoneyDueDateNotificationService::class.java)
    private val json = Json

    // 通知済みの対象期日（この日付分は送信済み）。daysBefore 分ずらした対象期日が
    // 日付跨ぎで自然に変わるため、ゴミ出し通知と異なり「日付が変わったらリセット」の処理は不要。
    internal var notifiedTargetDate: String? = null

    /** Application のコルーチンスコープから呼び出す。キャンセルで停止する。 */
    suspend fun runPollingLoop() {
        while (true) {
            try {
                checkAndNotify()
            } catch (e: Exception) {
                logger.warn("Money due date notification check failed", e)
            }
            delay(60_000L)
        }
    }

    internal suspend fun checkAndNotify(now: Instant = Instant.now()) {
        val settings = moneyRepository.getDueDateNotificationSettings()
        if (!settings.enabled || settings.webhookUrl.isBlank()) return

        val jstNow = now.atZone(JST)
        if (jstNow.hour < settings.notifyHour) return

        val targetDate = jstNow.toLocalDate().plusDays(settings.daysBefore.toLong()).toString()
        // 既に当該対象期日分を送信済み
        if (notifiedTargetDate == targetDate) return

        val dueItems = collectDueItems(targetDate)
        if (dueItems.isEmpty()) {
            notifiedTargetDate = targetDate
            return
        }

        sendWebhook(
            url = settings.webhookUrl,
            prefix = settings.prefix,
            targetDate = targetDate,
            items = dueItems,
        )
        notifiedTargetDate = targetDate
    }

    /** 全月データから dueDate == targetDate の項目を収集する（テスト用に internal） */
    internal suspend fun collectDueItems(targetDate: String): List<DueMoneyItem> =
        moneyRepository.getAllMonths().flatMap { monthly ->
            monthly.items
                .filter { it.dueDate == targetDate }
                .map { DueMoneyItem(yearMonth = monthly.yearMonth, name = it.name, amount = it.amount) }
        }

    /** テスト送信: 保存済み設定の daysBefore から対象期日を算出し即時送信する。該当項目がなければサンプル項目で送信する。 */
    suspend fun sendTestNotification() {
        val settings = moneyRepository.getDueDateNotificationSettings()
        require(settings.webhookUrl.isNotBlank()) { "Webhook URL が設定されていません" }
        val targetDate =
            Instant
                .now()
                .atZone(JST)
                .toLocalDate()
                .plusDays(settings.daysBefore.toLong())
                .toString()
        val items =
            collectDueItems(targetDate).ifEmpty {
                listOf(DueMoneyItem(yearMonth = targetDate.substring(0, 7), name = "(テスト項目)", amount = 1000L))
            }
        sendWebhook(url = settings.webhookUrl, prefix = settings.prefix, targetDate = targetDate, items = items)
    }

    internal suspend fun sendWebhook(
        url: String,
        prefix: String,
        targetDate: String,
        items: List<DueMoneyItem>,
    ) {
        val payload = buildPayload(url, prefix, targetDate, items)
        try {
            client.post(url) {
                setBody(TextContent(payload, ContentType.Application.Json))
            }
        } catch (e: Exception) {
            logger.warn("Money due date notification webhook failed", e)
        }
    }

    internal fun buildPayload(
        url: String,
        prefix: String,
        targetDate: String,
        items: List<DueMoneyItem>,
        moneyPageUrl: String? = appUrl?.let { "${it.trimEnd('/')}/money" },
    ): String {
        val total = items.sumOf { it.amount }
        val dateLabel = formatDueDateLabel(targetDate)

        fun message(sanitize: (String) -> String): String {
            val lines = items.joinToString("\n") { "・${sanitize(it.name)}（${formatAmount(it.amount)}）" }
            return "支払期日: ${dateLabel}の項目\n$lines\n合計 ${formatAmount(total)}"
        }

        return when (detectWebhookService(url)) {
            WebhookServiceType.DISCORD -> {
                val discord =
                    DiscordDueDatePayload(
                        content = prefix.ifBlank { null },
                        embeds =
                            listOf(
                                DiscordDueDateEmbed(
                                    title = "支払期日リマインダー",
                                    description = message(::sanitizeForDiscord),
                                    color = DISCORD_EMBED_COLOR,
                                    url = moneyPageUrl,
                                ),
                            ),
                    )
                json.encodeToString(discord)
            }
            WebhookServiceType.SLACK -> {
                val text = message(::sanitizeForSlack).let { if (prefix.isBlank()) it else "$prefix $it" }
                val withLink = if (moneyPageUrl != null) "$text\n<$moneyPageUrl|お金の管理を開く>" else text
                json.encodeToString(SlackDueDatePayload(text = withLink))
            }
            WebhookServiceType.GENERIC -> {
                json.encodeToString(
                    GenericDueDatePayload(
                        event = "money_due_date_reminder",
                        prefix = prefix.ifBlank { null },
                        dueDate = targetDate,
                        items = items.map { GenericDueDateItem(yearMonth = it.yearMonth, name = it.name, amount = it.amount) },
                        totalAmount = total,
                        message = message { it },
                        moneyPageUrl = moneyPageUrl,
                    ),
                )
            }
        }
    }

    companion object {
        private val appUrl: String? = EnvConfig["APP_URL"]

        /** "YYYY-MM-DD" を "YYYY年M月D日" 表記に整形。パース失敗時は入力をそのまま返す。 */
        internal fun formatDueDateLabel(dateStr: String): String {
            val parts = dateStr.split("-")
            if (parts.size != 3) return dateStr
            val month = parts[1].toIntOrNull() ?: return dateStr
            val day = parts[2].toIntOrNull() ?: return dateStr
            return "${parts[0]}年${month}月${day}日"
        }
    }
}

/** 期日通知の対象項目（月をまたいで収集するため yearMonth を保持する） */
internal data class DueMoneyItem(
    val yearMonth: String,
    val name: String,
    val amount: Long,
)

// --- Discord ペイロード ---

@Serializable
internal data class DiscordDueDatePayload(
    val content: String? = null,
    val embeds: List<DiscordDueDateEmbed>,
)

@Serializable
internal data class DiscordDueDateEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val url: String? = null,
)

// --- Slack ペイロード ---

@Serializable
internal data class SlackDueDatePayload(
    val text: String,
)

// --- 汎用ペイロード ---

@Serializable
internal data class GenericDueDateItem(
    val yearMonth: String,
    val name: String,
    val amount: Long,
)

@Serializable
internal data class GenericDueDatePayload(
    val event: String,
    val prefix: String? = null,
    val dueDate: String,
    val items: List<GenericDueDateItem>,
    val totalAmount: Long,
    val message: String,
    val moneyPageUrl: String? = null,
)
