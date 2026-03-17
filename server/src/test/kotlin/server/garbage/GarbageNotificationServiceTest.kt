package server.garbage

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import model.CollectionFrequency
import model.GarbageNotificationSettings
import model.GarbageType
import model.GarbageTypeSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GarbageNotificationServiceTest {
    private val garbageRepository = mockk<GarbageRepository>()
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

    private fun createService(): GarbageNotificationService =
        GarbageNotificationService(
            garbageRepository = garbageRepository,
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

    private val defaultSchedules =
        listOf(
            GarbageTypeSchedule(
                garbageType = GarbageType.BURNABLE,
                daysOfWeek = listOf(1, 4), // 月, 木
                frequency = CollectionFrequency.WEEKLY,
            ),
            GarbageTypeSchedule(
                garbageType = GarbageType.RECYCLABLE,
                daysOfWeek = listOf(3), // 水
                frequency = CollectionFrequency.WEEKLY,
            ),
        )

    // --- 無効 → 通知なし ---

    @Test
    fun disabledDoesNotNotify() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(enabled = false, webhookUrl = "https://discord.com/api/webhooks/x/y")

            val service = createService()
            service.checkAndNotify(jstInstant(10, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- URL 空 → 通知なし ---

    @Test
    fun emptyUrlDoesNotNotify() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(enabled = true, webhookUrl = "")

            val service = createService()
            service.checkAndNotify(jstInstant(10, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 通知時刻前 → 通知なし ---

    @Test
    fun beforeNotifyTimeDoesNotNotify() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    notifyHour = 10,
                )

            val service = createService()
            service.checkAndNotify(jstInstant(9, 59))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 通知時刻到達 & 翌日にゴミあり → 通知あり ---

    @Test
    fun atNotifyTimeWithGarbageSendsNotification() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    notifyHour = 10,
                )
            // 2026-03-18 (水) → 翌日 2026-03-19 (木), dayOfWeek=4
            coEvery { garbageRepository.getSchedules() } returns defaultSchedules

            val service = createService()
            service.checkAndNotify(jstInstant(10, 0, day = 18))

            assertTrue(webhookRequests.isNotEmpty())
        }

    // --- 翌日にゴミなし → 通知なし ---

    @Test
    fun noGarbageTomorrowDoesNotNotify() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    notifyHour = 10,
                )
            // 2026-03-20 (金) → 翌日 2026-03-21 (土), dayOfWeek=6 → ゴミなし
            coEvery { garbageRepository.getSchedules() } returns defaultSchedules

            val service = createService()
            service.checkAndNotify(jstInstant(10, 0, day = 20))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 同日重複通知なし ---

    @Test
    fun doesNotSendDuplicateNotification() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    notifyHour = 10,
                )
            coEvery { garbageRepository.getSchedules() } returns defaultSchedules

            val service = createService()
            // 1回目: 通知される (2026-03-18水 → 翌日木)
            service.checkAndNotify(jstInstant(10, 0, day = 18))
            val firstCount = webhookRequests.size

            // 2回目: 重複通知されない
            service.checkAndNotify(jstInstant(10, 5, day = 18))
            assertEquals(firstCount, webhookRequests.size)
        }

    // --- 翌日に日付が変わるとリセット ---

    @Test
    fun resetsOnNewDay() =
        runTest {
            coEvery { garbageRepository.getNotificationSettings() } returns
                GarbageNotificationSettings(
                    enabled = true,
                    webhookUrl = "https://discord.com/api/webhooks/x/y",
                    notifyHour = 10,
                )
            coEvery { garbageRepository.getSchedules() } returns defaultSchedules

            val service = createService()
            // 2026-03-18 (水) → 翌日は木でゴミあり
            service.checkAndNotify(jstInstant(10, 0, day = 18))
            val firstCount = webhookRequests.size

            // 2026-03-19 (木) → 翌日は金でゴミなし → 通知されないが notifiedDate はリセット
            service.checkAndNotify(jstInstant(10, 0, day = 19))
            assertEquals(firstCount, webhookRequests.size)
        }

    // --- weekOfMonth ---

    @Test
    fun weekOfMonthCalculation() {
        val jst = ZoneId.of("Asia/Tokyo")
        assertEquals(1, GarbageNotificationService.weekOfMonth(ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, jst)))
        assertEquals(1, GarbageNotificationService.weekOfMonth(ZonedDateTime.of(2026, 3, 7, 0, 0, 0, 0, jst)))
        assertEquals(2, GarbageNotificationService.weekOfMonth(ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, jst)))
        assertEquals(3, GarbageNotificationService.weekOfMonth(ZonedDateTime.of(2026, 3, 15, 0, 0, 0, 0, jst)))
        assertEquals(4, GarbageNotificationService.weekOfMonth(ZonedDateTime.of(2026, 3, 22, 0, 0, 0, 0, jst)))
        assertEquals(5, GarbageNotificationService.weekOfMonth(ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, jst)))
    }

    // --- Discord ペイロード ---

    @Test
    fun discordPayloadContainsEmbed() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://discord.com/api/webhooks/x/y",
                prefix = "@everyone",
                garbageTypes = listOf(GarbageType.BURNABLE),
                dashboardUrl = "https://example.com",
            )
        assertTrue(payload.contains("embeds"))
        assertTrue(payload.contains("@everyone"))
        assertTrue(payload.contains("可燃ゴミ"))
        assertTrue(payload.contains("https://example.com"))
    }

    // --- Slack ペイロード ---

    @Test
    fun slackPayloadContainsLink() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://hooks.slack.com/services/x/y/z",
                prefix = "",
                garbageTypes = listOf(GarbageType.BURNABLE, GarbageType.RECYCLABLE),
                dashboardUrl = "https://example.com",
            )
        assertTrue(payload.contains("\"text\""))
        assertTrue(payload.contains("可燃ゴミ"))
        assertTrue(payload.contains("資源ゴミ"))
        assertTrue(payload.contains("<https://example.com|"))
    }

    // --- 汎用ペイロード ---

    @Test
    fun genericPayloadContainsTypes() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "",
                garbageTypes = listOf(GarbageType.NON_BURNABLE),
                dashboardUrl = null,
            )
        assertTrue(payload.contains("garbage_reminder"))
        assertTrue(payload.contains("NON_BURNABLE"))
        assertTrue(payload.contains("不燃ゴミ"))
        assertFalse(payload.contains("dashboardUrl"))
    }
}
