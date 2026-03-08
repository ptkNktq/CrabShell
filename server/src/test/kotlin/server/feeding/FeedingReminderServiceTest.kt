package server.feeding

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import model.Feeding
import model.FeedingLog
import model.FeedingSettings
import model.MealTime
import model.Pet
import model.WebhookEvent
import server.pet.PetRepository
import server.quest.WebhookService
import java.time.Instant
import java.time.LocalTime
import kotlin.test.Test

class FeedingReminderServiceTest {
    private val feedingRepository = mockk<FeedingRepository>()
    private val feedingSettingsRepository = mockk<FeedingSettingsRepository>()
    private val petRepository = mockk<PetRepository>()
    private val webhookService = mockk<WebhookService>(relaxed = true)
    private val service =
        FeedingReminderService(
            feedingRepository,
            feedingSettingsRepository,
            petRepository,
            webhookService,
        )

    private val testPet = Pet(id = "pet1", name = "ぬい")
    private val defaultSettings =
        FeedingSettings(
            reminderEnabled = true,
            mealTimes =
                mapOf(
                    MealTime.MORNING to "07:00",
                    MealTime.LUNCH to "12:00",
                    MealTime.EVENING to "18:00",
                ),
            reminderDelayMinutes = 30,
        )

    private fun instantAt(
        hour: Int,
        minute: Int,
    ): Instant {
        val date = java.time.LocalDate.of(2026, 3, 8)
        val time = LocalTime.of(hour, minute)
        return date.atTime(time).atZone(FeedingReminderService.ZONE).toInstant()
    }

    private fun emptyLog(date: String): FeedingLog = FeedingLog(date = date)

    private fun doneLog(
        date: String,
        vararg meals: MealTime,
    ): FeedingLog =
        FeedingLog(
            date = date,
            feedings =
                MealTime.entries.associateWith { meal ->
                    if (meal in meals) Feeding(done = true, timestamp = "2026-03-08T10:00:00Z") else Feeding()
                },
        )

    @Test
    fun skipsWhenReminderDisabled() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns
                defaultSettings.copy(reminderEnabled = false)

            service.checkAndNotify(instantAt(20, 0))

            coVerify(exactly = 0) { webhookService.notify(any<String>(), any<String>(), any<String>()) }
        }

    @Test
    fun sendsReminderWhenMealNotDoneAfterDelay() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings
            coEvery { petRepository.getPets() } returns listOf(testPet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-08") } returns emptyLog("2026-03-08")

            // 12:30 → LUNCH のリマインダー時刻（12:00 + 30分）
            service.checkAndNotify(instantAt(12, 30))

            coVerify {
                webhookService.notify(
                    event = WebhookEvent.FEEDING_REMINDER,
                    title = any(),
                    description = match { it.contains("昼") },
                )
            }
        }

    @Test
    fun doesNotSendReminderBeforeDelayTime() =
        runTest {
            val lunchOnlySettings =
                defaultSettings.copy(
                    mealTimes = mapOf(MealTime.LUNCH to "12:00"),
                )
            coEvery { feedingSettingsRepository.getSettings() } returns lunchOnlySettings
            coEvery { petRepository.getPets() } returns listOf(testPet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-08") } returns emptyLog("2026-03-08")

            // 12:29 → まだリマインダー時刻（12:30）に達していない
            service.checkAndNotify(instantAt(12, 29))

            coVerify(exactly = 0) { webhookService.notify(any<String>(), any<String>(), any<String>()) }
        }

    @Test
    fun doesNotSendReminderWhenMealIsDone() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings
            coEvery { petRepository.getPets() } returns listOf(testPet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-08") } returns
                doneLog("2026-03-08", MealTime.LUNCH)

            service.checkAndNotify(instantAt(13, 0))

            // LUNCH は done なのでリマインダーなし。MORNING は 07:30 を過ぎているので送信。
            coVerify(exactly = 1) { webhookService.notify(any<String>(), any<String>(), any<String>()) }
            coVerify {
                webhookService.notify(
                    event = WebhookEvent.FEEDING_REMINDER,
                    title = any(),
                    description = match { it.contains("朝") },
                )
            }
        }

    @Test
    fun doesNotSendDuplicateReminder() =
        runTest {
            coEvery { feedingSettingsRepository.getSettings() } returns defaultSettings
            coEvery { petRepository.getPets() } returns listOf(testPet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-08") } returns emptyLog("2026-03-08")

            // 1回目: リマインダー送信
            service.checkAndNotify(instantAt(12, 30))
            // 2回目: 同じ食事・日付は重複送信しない
            service.checkAndNotify(instantAt(12, 35))

            // LUNCH のリマインダーは1回だけ（MORNING は 07:30 で2回チェックだが1回のみ送信）
            coVerify(exactly = 1) {
                webhookService.notify(
                    event = WebhookEvent.FEEDING_REMINDER,
                    title = any(),
                    description = match { it.contains("昼") },
                )
            }
        }

    @Test
    fun sendsRemindersForMultiplePets() =
        runTest {
            val pet2 = Pet(id = "pet2", name = "もち")
            coEvery { feedingSettingsRepository.getSettings() } returns
                defaultSettings.copy(
                    mealTimes = mapOf(MealTime.LUNCH to "12:00"),
                )
            coEvery { petRepository.getPets() } returns listOf(testPet, pet2)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-08") } returns emptyLog("2026-03-08")
            coEvery { feedingRepository.getFeedingLog("pet2", "2026-03-08") } returns emptyLog("2026-03-08")

            service.checkAndNotify(instantAt(12, 30))

            coVerify(exactly = 2) { webhookService.notify(any<String>(), any<String>(), any<String>()) }
        }
}
