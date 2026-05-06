package server.feeding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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

class FeedingNotificationServiceTest {
    private val feedingRepository = mockk<FeedingRepository>()
    private val feedingSettingsRepository = mockk<FeedingSettingsRepository>()
    private val petRepository = mockk<PetRepository>()
    private val webhookRequests = mutableListOf<String>()
    private val webhookBodies = mutableListOf<String>()

    private val mockClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    webhookRequests.add(request.url.toString())
                    val body = (request.body as? TextContent)?.text ?: ""
                    webhookBodies.add(body)
                    respond("ok", HttpStatusCode.OK)
                }
            }
        }

    private fun createService(): FeedingNotificationService =
        FeedingNotificationService(
            feedingRepository = feedingRepository,
            feedingSettingsRepository = feedingSettingsRepository,
            petRepository = petRepository,
            client = mockClient,
        )

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

    private fun defaultSettings(
        enabled: Boolean = true,
        url: String = "https://discord.com/api/webhooks/x/y",
        delay: Int = 30,
    ) = FeedingSettings(
        reminderEnabled = enabled,
        reminderWebhookUrl = url,
        reminderDelayMinutes = delay,
    )

    // --- reminderEnabled = false → 通知なし ---

    @Test
    fun disabledReminderDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings(enabled = false)

            val service = createService()
            service.checkAndNotify(jstInstant(13, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- webhook URL 空 → 通知なし ---

    @Test
    fun emptyWebhookUrlDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings(url = "")

            val service = createService()
            service.checkAndNotify(jstInstant(13, 0))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 予定時刻前 → 通知なし ---

    @Test
    fun beforeScheduledDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 6:59 → MORNING 7:00 前。どの食事も予定時刻に達していない
            service.checkAndNotify(jstInstant(6, 59))

            assertTrue(webhookRequests.isEmpty())
        }

    // --- 予定時刻到達（リマインダー時刻前） → SCHEDULED 通知 ---

    @Test
    fun pastScheduledBeforeReminderSendsScheduledOnly() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                FeedingSettings(
                    reminderEnabled = true,
                    reminderWebhookUrl = "https://discord.com/api/webhooks/x/y",
                    mealTimes = mapOf(MealTime.LUNCH to "12:00"),
                    reminderDelayMinutes = 30,
                )
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 12:15 → LUNCH 12:00 を過ぎ、12:30 リマインダー前。SCHEDULED のみ発火
            service.checkAndNotify(jstInstant(12, 15))

            assertEquals(1, webhookRequests.size)
            assertTrue(webhookBodies.last().contains("給餌時間"))
            assertFalse(webhookBodies.last().contains("給餌リマインダー"))
        }

    // --- 食事ごとに phase が混在しても並行発火する ---

    @Test
    fun differentMealsCanFireDifferentPhasesSimultaneously() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 12:15 → MORNING は REMINDER（12:15 >= 7:30）、LUNCH は SCHEDULED（12:00 <= 12:15 < 12:30）
            service.checkAndNotify(jstInstant(12, 15))

            assertEquals(2, webhookRequests.size)
            assertTrue(webhookBodies.any { it.contains("給餌時間") }, "LUNCH の SCHEDULED 文言が含まれる")
            assertTrue(webhookBodies.any { it.contains("給餌リマインダー") }, "MORNING の REMINDER 文言が含まれる")
        }

    // --- リマインダー時刻到達 & 未記録 → REMINDER 通知 ---

    @Test
    fun pastReminderAndUnrecordedSendsReminder() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 12:31 → MORNING / LUNCH の REMINDER（7:30 / 12:30）を過ぎている。EVENING は未到達。
            service.checkAndNotify(jstInstant(12, 31))

            assertEquals(2, webhookRequests.size)
            assertTrue(webhookBodies.all { it.contains("給餌リマインダー") })
        }

    // --- SCHEDULED 送信後、REMINDER 時刻到達で REMINDER のみ追加送信 ---

    @Test
    fun scheduledThenReminderSendsBoth() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 7:00: MORNING SCHEDULED 発火（LUNCH/EVENING はまだ）
            service.checkAndNotify(jstInstant(7, 0))
            assertEquals(1, webhookRequests.size)
            assertTrue(webhookBodies.last().contains("給餌時間"))

            // 7:31: MORNING REMINDER 発火、SCHEDULED 重複なし
            service.checkAndNotify(jstInstant(7, 31))
            assertEquals(2, webhookRequests.size)
            assertTrue(webhookBodies.last().contains("給餌リマインダー"))

            // 7:35: 既に REMINDER 通知済み、追加送信なし
            service.checkAndNotify(jstInstant(7, 35))
            assertEquals(2, webhookRequests.size)
        }

    // --- リマインダー時刻直行: SCHEDULED は取りこぼし扱いでマークのみ ---

    @Test
    fun jumpingPastReminderSendsReminderOnceAndSuppressesScheduled() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            // 7:31: いきなり REMINDER 時刻を過ぎている → REMINDER 発火、SCHEDULED は遅れて来ない
            service.checkAndNotify(jstInstant(7, 31))
            assertEquals(1, webhookRequests.size)
            assertTrue(webhookBodies.last().contains("給餌リマインダー"))

            // 7:32: SCHEDULED も REMINDER も追加送信されない
            service.checkAndNotify(jstInstant(7, 32))
            assertEquals(1, webhookRequests.size)
        }

    // --- 既に記録済み → どの phase も通知しない ---

    @Test
    fun alreadyRecordedDoesNotNotify() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
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

    // --- 同一 phase の重複通知なし ---

    @Test
    fun doesNotSendDuplicateNotification() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings()
            coEvery { petRepository.getPets() } returns listOf(Pet(id = "pet1", name = "ポチ"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns
                FeedingLog(date = "2026-03-14")

            val service = createService()
            service.checkAndNotify(jstInstant(12, 31))
            val firstCount = webhookRequests.size

            service.checkAndNotify(jstInstant(12, 35))
            assertEquals(firstCount, webhookRequests.size)
        }

    // --- feedingDate: 5:00 AM 前は前日扱い ---

    @Test
    fun feedingDateBefore5amReturnsPreviousDay() {
        val jstNow = ZonedDateTime.of(2026, 3, 14, 4, 59, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-03-13", FeedingNotificationService.feedingDate(jstNow))
    }

    @Test
    fun feedingDateAt5amReturnsCurrentDay() {
        val jstNow = ZonedDateTime.of(2026, 3, 14, 5, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-03-14", FeedingNotificationService.feedingDate(jstNow))
    }

    // --- isPastTime ---

    @Test
    fun isPastTimeAfterTarget() {
        assertTrue(
            FeedingNotificationService.isPastTime(
                currentTime = LocalTime.of(12, 31),
                target = LocalTime.of(12, 30),
            ),
        )
    }

    @Test
    fun isPastTimeBeforeTarget() {
        assertFalse(
            FeedingNotificationService.isPastTime(
                currentTime = LocalTime.of(12, 29),
                target = LocalTime.of(12, 30),
            ),
        )
    }

    // --- Webhook ペイロード（REMINDER） ---

    @Test
    fun discordReminderPayloadContainsTitle() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://discord.com/api/webhooks/x/y",
                prefix = "@everyone",
                phase = FeedingNotificationPhase.REMINDER,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "昼",
                scheduledTime = "12:00",
                feedingPageUrl = "https://example.com/feeding",
            )
        assertTrue(payload.contains("embeds"))
        assertTrue(payload.contains("@everyone"))
        assertTrue(payload.contains("ポチ"))
        assertTrue(payload.contains("給餌リマインダー"))
        assertTrue(payload.contains("まだ記録されていません"))
        assertTrue(payload.contains("https://example.com/feeding"))
        assertFalse(payload.contains("fields"))
    }

    @Test
    fun discordScheduledPayloadContainsScheduledTitle() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://discord.com/api/webhooks/x/y",
                prefix = "",
                phase = FeedingNotificationPhase.SCHEDULED,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "朝",
                scheduledTime = "07:00",
                feedingPageUrl = "https://example.com/feeding",
            )
        assertTrue(payload.contains("給餌時間"))
        assertTrue(payload.contains("ごはんの時間です"))
        assertFalse(payload.contains("給餌リマインダー"))
        assertFalse(payload.contains("まだ記録されていません"))
    }

    @Test
    fun slackPayloadContainsLink() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://hooks.slack.com/services/x/y/z",
                prefix = "",
                phase = FeedingNotificationPhase.REMINDER,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "朝",
                scheduledTime = "07:00",
                feedingPageUrl = "https://example.com/feeding",
            )
        assertTrue(payload.contains("\"text\""))
        assertTrue(payload.contains("ポチ"))
        assertTrue(payload.contains("<https://example.com/feeding|"))
    }

    @Test
    fun slackScheduledPayloadHasScheduledMessage() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://hooks.slack.com/services/x/y/z",
                prefix = "",
                phase = FeedingNotificationPhase.SCHEDULED,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "晩",
                scheduledTime = "18:00",
                feedingPageUrl = null,
            )
        assertTrue(payload.contains("ごはんの時間です"))
        assertFalse(payload.contains("まだ記録されていません"))
    }

    @Test
    fun genericReminderPayloadHasReminderEvent() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "",
                phase = FeedingNotificationPhase.REMINDER,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "晩",
                scheduledTime = "18:00",
                feedingPageUrl = "https://example.com/feeding",
            )
        assertTrue(payload.contains("\"event\":\"feeding_reminder\""))
        assertTrue(payload.contains("ポチ"))
        assertTrue(payload.contains("https://example.com/feeding"))
    }

    @Test
    fun genericScheduledPayloadHasScheduledEvent() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "",
                phase = FeedingNotificationPhase.SCHEDULED,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "朝",
                scheduledTime = "07:00",
                feedingPageUrl = null,
            )
        assertTrue(payload.contains("\"event\":\"feeding_scheduled\""))
        assertFalse(payload.contains("\"event\":\"feeding_reminder\""))
    }

    @Test
    fun genericPayloadContainsPrefixWhenSet() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "@channel",
                phase = FeedingNotificationPhase.REMINDER,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "朝",
                scheduledTime = "07:00",
            )
        assertTrue(payload.contains("\"prefix\":\"@channel\""))
    }

    @Test
    fun genericPayloadOmitsPrefixWhenBlank() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://example.com/webhook",
                prefix = "",
                phase = FeedingNotificationPhase.REMINDER,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "朝",
                scheduledTime = "07:00",
            )
        assertFalse(payload.contains("\"prefix\""))
    }

    @Test
    fun discordPayloadWithoutAppUrlOmitsLink() {
        val service = createService()
        val payload =
            service.buildPayload(
                url = "https://discord.com/api/webhooks/x/y",
                prefix = "",
                phase = FeedingNotificationPhase.REMINDER,
                petId = "pet1",
                petName = "ポチ",
                mealLabel = "昼",
                scheduledTime = "12:00",
                feedingPageUrl = null,
            )
        assertTrue(payload.contains("embeds"))
        // embed.url が null なので、リンク (https://example.com/feeding) は payload に出ない
        assertFalse(payload.contains("example.com"))
    }
}
