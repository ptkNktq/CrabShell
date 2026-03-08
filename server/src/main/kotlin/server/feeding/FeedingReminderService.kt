package server.feeding

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import model.MealTime
import model.WebhookEvent
import org.slf4j.LoggerFactory
import server.pet.PetRepository
import server.quest.WebhookService
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("FeedingReminderService")

class FeedingReminderService(
    private val feedingRepository: FeedingRepository,
    private val feedingSettingsRepository: FeedingSettingsRepository,
    private val petRepository: PetRepository,
    private val webhookService: WebhookService,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // key: "petId:date", value: 通知済み MealTime セット
    private val notifiedMap = ConcurrentHashMap<String, MutableSet<MealTime>>()

    fun start() {
        scope.launch {
            while (isActive) {
                try {
                    checkAndNotify()
                } catch (e: Exception) {
                    logger.warn("Reminder check failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    internal suspend fun checkAndNotify(now: Instant = Instant.now()) {
        val settings = feedingSettingsRepository.getSettings()
        if (!settings.reminderEnabled) return

        val zoned = now.atZone(ZONE)
        val today = zoned.toLocalDate().toString()
        val currentTime = zoned.toLocalTime()
        val pets = petRepository.getPets()

        // 日付変更で前日分をクリア
        cleanupOldEntries(today)

        for (pet in pets) {
            val log = feedingRepository.getFeedingLog(pet.id, today)
            for ((mealTime, scheduledTimeStr) in settings.mealTimes) {
                val scheduledTime = LocalTime.parse(scheduledTimeStr)
                val reminderTime = scheduledTime.plusMinutes(settings.reminderDelayMinutes.toLong())

                if (currentTime >= reminderTime && log.feedings[mealTime]?.done != true) {
                    if (!alreadyNotified(pet.id, today, mealTime)) {
                        val mealLabel = mealTimeLabel(mealTime)
                        webhookService.notify(
                            event = WebhookEvent.FEEDING_REMINDER,
                            title = "${settings.reminderPrefix}: ${pet.name}",
                            description = "${mealLabel}ごはん（予定: $scheduledTimeStr）がまだ記録されていません",
                        )
                        markNotified(pet.id, today, mealTime)
                        logger.info("Sent reminder for pet=${pet.name} meal=$mealTime")
                    }
                }
            }
        }
    }

    private fun alreadyNotified(
        petId: String,
        date: String,
        mealTime: MealTime,
    ): Boolean {
        val key = "$petId:$date"
        return notifiedMap[key]?.contains(mealTime) == true
    }

    private fun markNotified(
        petId: String,
        date: String,
        mealTime: MealTime,
    ) {
        val key = "$petId:$date"
        notifiedMap.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(mealTime)
    }

    private fun cleanupOldEntries(today: String) {
        notifiedMap.keys().asIterator().forEach { key ->
            if (!key.endsWith(":$today")) {
                notifiedMap.remove(key)
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
        const val POLL_INTERVAL_MS = 60_000L
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}
