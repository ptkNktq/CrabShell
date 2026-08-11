package server.money

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import model.MoneyDueDateNotificationSettings
import model.MoneyItem
import model.MonthlyMoney
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyDueDateNotificationServiceTest {
    private val moneyRepository = mockk<MoneyRepository>()
    private val webhookRequests = mutableListOf<String>()

    private val mockClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    webhookRequests.add(request.url.toString())
                    respond("ok", HttpStatusCode.OK)
                }
            }
        }

    private fun createService(): MoneyDueDateNotificationService =
        MoneyDueDateNotificationService(
            moneyRepository = moneyRepository,
            client = mockClient,
        )

    private fun jstInstant(
        hour: Int,
        minute: Int = 0,
        year: Int = 2026,
        month: Int = 3,
        day: Int = 18,
    ): Instant =
        ZonedDateTime
            .of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Tokyo"))
            .toInstant()

    private val monthWithDueItem =
        listOf(
            MonthlyMoney(
                yearMonth = "2026-03",
                items =
                    listOf(
                        MoneyItem(id = "i1", name = "電気代", amount = 8000L, dueDate = "2026-03-19"),
                        MoneyItem(id = "i2", name = "水道代", amount = 3000L, dueDate = "2026-03-25"),
                    ),
            ),
        )

    // --- 無効 → 通知なし ---

    @Test
    fun disabledDoesNotNotify() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(enabled = false, webhookUrl = "https://discord.com/api/webhooks/x/y")

            val service = createService()
            service.checkAndNotify(jstInstant(23, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- URL 空 → 通知なし ---

    @Test
    fun emptyUrlDoesNotNotify() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(enabled = true, webhookUrl = "")

            val service = createService()
            service.checkAndNotify(jstInstant(23, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 通知時刻前 → 通知なし ---

    @Test
    fun beforeNotifyHourDoesNotNotify() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    notifyHour = 23,
                )

            val service = createService()
            service.checkAndNotify(jstInstant(22, 59))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 通知時刻到達 & daysBefore 日後に期日の項目あり → 通知あり ---

    @Test
    fun atNotifyHourWithDueItemSendsNotification() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    daysBefore = 1,
                    notifyHour = 23,
                )
            coEvery { moneyRepository.getAllMonths() } returns monthWithDueItem

            val service = createService()
            // 2026-03-18 23:00 + 1日前 = 2026-03-19 が期日の項目あり
            service.checkAndNotify(jstInstant(23, 0, day = 18))

            assertTrue(webhookRequests.isNotEmpty())
        }

    // --- 対象期日に項目なし → 通知なし ---

    @Test
    fun noDueItemsOnTargetDateDoesNotNotify() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    daysBefore = 1,
                    notifyHour = 23,
                )
            coEvery { moneyRepository.getAllMonths() } returns monthWithDueItem

            val service = createService()
            // 2026-03-20 23:00 + 1日前 = 2026-03-21 は該当項目なし
            service.checkAndNotify(jstInstant(23, 0, day = 20))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 同一対象期日の重複通知なし ---

    @Test
    fun doesNotSendDuplicateNotificationForSameTargetDate() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    daysBefore = 1,
                    notifyHour = 23,
                )
            coEvery { moneyRepository.getAllMonths() } returns monthWithDueItem

            val service = createService()
            service.checkAndNotify(jstInstant(23, 0, day = 18))
            val firstCount = webhookRequests.size

            service.checkAndNotify(jstInstant(23, 5, day = 18))
            assertEquals(firstCount, webhookRequests.size)
        }

    // --- 日付が変わり対象期日が変われば再度通知される ---

    @Test
    fun notifiesAgainWhenTargetDateAdvances() =
        runTest {
            coEvery { moneyRepository.getDueDateNotificationSettings() } returns
                MoneyDueDateNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    daysBefore = 1,
                    notifyHour = 23,
                )
            coEvery { moneyRepository.getAllMonths() } returns monthWithDueItem

            val service = createService()
            // 対象期日 2026-03-19 の項目で通知
            service.checkAndNotify(jstInstant(23, 0, day = 18))
            val firstCount = webhookRequests.size
            assertTrue(firstCount > 0)

            // 対象期日 2026-03-25 の項目で再度通知
            service.checkAndNotify(jstInstant(23, 0, day = 24))
            assertTrue(webhookRequests.size > firstCount)
        }

    // --- collectDueItems: 全月を横断して収集 ---

    @Test
    fun collectDueItemsScansAllMonths() =
        runTest {
            coEvery { moneyRepository.getAllMonths() } returns
                listOf(
                    MonthlyMoney(
                        yearMonth = "2026-03",
                        items = listOf(MoneyItem(id = "i1", name = "電気代", amount = 8000L, dueDate = "2026-04-01")),
                    ),
                    MonthlyMoney(
                        yearMonth = "2026-04",
                        items = listOf(MoneyItem(id = "i2", name = "家賃", amount = 80000L, dueDate = "2026-04-01")),
                    ),
                )

            val service = createService()
            val dueItems = service.collectDueItems("2026-04-01")

            assertEquals(2, dueItems.size)
            assertTrue(dueItems.any { it.yearMonth == "2026-03" && it.name == "電気代" })
            assertTrue(dueItems.any { it.yearMonth == "2026-04" && it.name == "家賃" })
        }

    // --- Discord ペイロード ---

    @Test
    fun discordPayloadContainsEmbed() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://discord.com/api/webhooks/x/y",
                prefix = "@everyone",
                targetDate = "2026-03-19",
                items = listOf(DueMoneyItem(yearMonth = "2026-03", name = "電気代", amount = 8000L)),
                moneyPageUrl = "https://example.com/money",
            )
        assertTrue(payload.contains("embeds"))
        assertTrue(payload.contains("@everyone"))
        assertTrue(payload.contains("電気代"))
        assertTrue(payload.contains("https://example.com/money"))
    }

    // --- Slack ペイロード ---

    @Test
    fun slackPayloadContainsLink() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://hooks.slack.com/services/x/y/z",
                prefix = "",
                targetDate = "2026-03-19",
                items = listOf(DueMoneyItem(yearMonth = "2026-03", name = "電気代", amount = 8000L)),
                moneyPageUrl = "https://example.com/money",
            )
        assertTrue(payload.contains("\"text\""))
        assertTrue(payload.contains("電気代"))
        assertTrue(payload.contains("<https://example.com/money|"))
    }

    // --- 汎用ペイロード ---

    @Test
    fun genericPayloadContainsItems() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "",
                targetDate = "2026-03-19",
                items = listOf(DueMoneyItem(yearMonth = "2026-03", name = "電気代", amount = 8000L)),
                moneyPageUrl = null,
            )
        assertTrue(payload.contains("money_due_date_reminder"))
        assertTrue(payload.contains("電気代"))
        assertTrue(payload.contains("2026-03-19"))
        assertFalse(payload.contains("moneyPageUrl\":\""))
    }

    // --- formatDueDateLabel ---

    @Test
    fun formatDueDateLabelFormatsCorrectly() {
        assertEquals("2026年3月19日", MoneyDueDateNotificationService.formatDueDateLabel("2026-03-19"))
    }
}
