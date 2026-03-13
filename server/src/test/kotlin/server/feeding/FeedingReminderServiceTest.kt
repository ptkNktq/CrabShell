package server.feeding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import model.Feeding
import model.FeedingLog
import model.FeedingSettings
import model.MealTime
import model.Pet
import server.pet.PetRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedingReminderServiceTest {
    private val feedingRepository = mockk<FeedingRepository>()
    private val feedingSettingsRepository = mockk<FeedingSettingsRepository>()
    private val petRepository = mockk<PetRepository>()
    private val webhookRequests = mutableListOf<String>()

    private fun createService(): FeedingReminderService {
        val service =
            FeedingReminderService(
                feedingRepository = feedingRepository,
                feedingSettingsRepository = feedingSettingsRepository,
                petRepository = petRepository,
            )
        service.clientFactory = {
            HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        webhookRequests.add(request.url.toString())
                        respond("ok", HttpStatusCode.OK)
                    }
                }
            }
        }
        return service
    }

    private fun jstInstant(
        hour: Int,
        minute: Int = 0,
        year: Int = 2026,
        month: Int = 3,
        day: Int = 14,
    ): Instant =
        ZonedDateTime
            .of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Tokyo"))
            .toInstant()

    // --- reminderEnabled = false → 通知なし ---

    @Test
    fun disabledReminderDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(reminderEnabled = false, reminderWebhookUrl = "https://discord.com/api/webhooks/x/y")

            val service = createService()
            service.checkAndNotify(jstInstant(13, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- webhook URL 空 → 通知なし ---

    @Test
    fun emptyWebhookUrlDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(reminderEnabled = true, reminderWebhookUrl = "")

            val service = createService()
            service.checkAndNotify(jstInstant(13, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 予定時刻+遅延 未到達 → 通知なし ---

    @Test
    fun beforeDelayDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(
                    reminderEnabled = true,
                    reminderWebhookUrl = "https://discord.com/api/webhooks/x/y",
                    reminderDelayMinutes = 30,
                )
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 最も早い MORNING 7:00 + 30min = 7:30, 現在 7:29 → まだ通知しない
            service.checkAndNotify(jstInstant(7, 29))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 予定時刻+遅延 経過 & 未記録 → 通知あり ---

    @Test
    fun pastDelayAndUnrecordedSendsNotification() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(
                    reminderEnabled = true,
                    reminderWebhookUrl = "https://discord.com/api/webhooks/x/y",
                    reminderDelayMinutes = 30,
                )
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 12:00 + 30min = 12:30, 現在 12:31 → 通知する
            service.checkAndNotify(jstInstant(12, 31))

            assertTrue(webhookRequests.isNotEmpty())
        }

    // --- 既に記録済み → 通知なし ---

    @Test
    fun alreadyRecordedDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(
                    reminderEnabled = true,
                    reminderWebhookUrl = "https://discord.com/api/webhooks/x/y",
                    reminderDelayMinutes = 30,
                )
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(
                    date = "2026-03-14",
                    feedings =
                        mapOf(
                            MealTime.MORNING to Feeding(done = true, timestamp = "2026-03-14T07:30:00Z"),
                            MealTime.LUNCH to Feeding(done = true, timestamp = "2026-03-14T12:15:00Z"),
                            MealTime.EVENING to Feeding(done = true, timestamp = "2026-03-14T18:20:00Z"),
                        ),
                )

            val service = createService()
            service.checkAndNotify(jstInstant(19, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 同日重複通知なし（notifiedMap） ---

    @Test
    fun doesNotSendDuplicateNotification() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(
                    reminderEnabled = true,
                    reminderWebhookUrl = "https://discord.com/api/webhooks/x/y",
                    reminderDelayMinutes = 30,
                )
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 1回目: 通知される
            service.checkAndNotify(jstInstant(12, 31))
            val firstCount = webhookRequests.size

            // 2回目: 重複通知されない
            service.checkAndNotify(jstInstant(12, 35))
            assertEquals(firstCount, webhookRequests.size)
        }

    // --- feedingDate: 5:00 AM 前は前日扱い ---

    @Test
    fun feedingDateBefore5amReturnsPreviousDay() {
        val jstNow = ZonedDateTime.of(2026, 3, 14, 4, 59, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-03-13", FeedingReminderService.feedingDate(jstNow))
    }

    @Test
    fun feedingDateAt5amReturnsCurrentDay() {
        val jstNow = ZonedDateTime.of(2026, 3, 14, 5, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-03-14", FeedingReminderService.feedingDate(jstNow))
    }

    // --- isPastReminderTime ---

    @Test
    fun isPastReminderTimeAfterSchedule() {
        assertTrue(
            FeedingReminderService.isPastReminderTime(
                currentTime = LocalTime.of(12, 31),
                reminderTime = LocalTime.of(12, 30),
            ),
        )
    }

    @Test
    fun isPastReminderTimeBeforeSchedule() {
        assertFalse(
            FeedingReminderService.isPastReminderTime(
                currentTime = LocalTime.of(12, 29),
                reminderTime = LocalTime.of(12, 30),
            ),
        )
    }

    // --- Webhook ペイロード ---

    @Test
    fun discordPayloadContainsEmbed() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://discord.com/api/webhooks/x/y",
                prefix = "@everyone",
                petName = "ポチ",
                mealLabel = "昼",
                scheduledTime = "12:00",
            )
        assertTrue(payload.contains("embeds"))
        assertTrue(payload.contains("@everyone"))
        assertTrue(payload.contains("ポチ"))
    }

    @Test
    fun slackPayloadContainsText() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://hooks.slack.com/services/x/y/z",
                prefix = "",
                petName = "ポチ",
                mealLabel = "朝",
                scheduledTime = "07:00",
            )
        assertTrue(payload.contains("\"text\""))
        assertTrue(payload.contains("ポチ"))
    }

    @Test
    fun genericPayloadContainsEvent() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "",
                petName = "ポチ",
                mealLabel = "晩",
                scheduledTime = "18:00",
            )
        assertTrue(payload.contains("feeding_reminder"))
        assertTrue(payload.contains("ポチ"))
    }
}
