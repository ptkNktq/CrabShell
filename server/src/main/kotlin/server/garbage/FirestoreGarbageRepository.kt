package server.garbage

import com.google.cloud.firestore.Firestore
import model.CollectionFrequency
import model.GarbageNotificationSettings
import model.GarbageType
import model.GarbageTypeSchedule
import server.cache.Cacheable
import server.util.await
import java.util.concurrent.ConcurrentHashMap

private const val SETTINGS_COLLECTION = "settings"
private const val GARBAGE_DOC = "garbage_schedule"
private const val ENTRIES_FIELD = "entries"
private const val NOTIFICATION_DOC = "garbage_notification"

class FirestoreGarbageRepository(
    private val firestore: Firestore,
) : GarbageRepository,
    Cacheable {
    override val cacheName: String = "garbage"

    override fun clearCache() {
        cache.clear()
    }

    private val cache = ConcurrentHashMap<String, List<GarbageTypeSchedule>>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun getSchedules(): List<GarbageTypeSchedule> {
        cache["all"]?.let { return it }

        val doc =
            firestore
                .collection(SETTINGS_COLLECTION)
                .document(GARBAGE_DOC)
                .get()
                .await()

        if (!doc.exists()) {
            cache["all"] = emptyList()
            return emptyList()
        }

        val entriesRaw = doc.get(ENTRIES_FIELD) as? List<Map<String, Any?>>
        if (entriesRaw == null) {
            cache["all"] = emptyList()
            return emptyList()
        }

        val schedules =
            entriesRaw.map { entry ->
                GarbageTypeSchedule(
                    garbageType = GarbageType.valueOf(entry["garbageType"] as String),
                    daysOfWeek = (entry["daysOfWeek"] as List<*>).map { (it as Long).toInt() },
                    frequency =
                        CollectionFrequency.valueOf(
                            entry["frequency"] as? String ?: "WEEKLY",
                        ),
                )
            }
        cache["all"] = schedules
        return schedules
    }

    override suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>) {
        val entries =
            schedules.map { schedule ->
                mapOf(
                    "garbageType" to schedule.garbageType.name,
                    "daysOfWeek" to schedule.daysOfWeek,
                    "frequency" to schedule.frequency.name,
                )
            }

        firestore
            .collection(SETTINGS_COLLECTION)
            .document(GARBAGE_DOC)
            .set(mapOf(ENTRIES_FIELD to entries))
            .await()

        cache["all"] = schedules
    }

    override suspend fun getNotificationSettings(): GarbageNotificationSettings {
        val doc =
            firestore
                .collection(SETTINGS_COLLECTION)
                .document(NOTIFICATION_DOC)
                .get()
                .await()

        if (!doc.exists()) return GarbageNotificationSettings()

        return GarbageNotificationSettings(
            enabled = doc.getBoolean("enabled") ?: false,
            webhookUrl = doc.getString("webhookUrl") ?: "",
            notifyTime = doc.getString("notifyTime") ?: "10:00",
            prefix = doc.getString("prefix") ?: "",
        )
    }

    override suspend fun saveNotificationSettings(settings: GarbageNotificationSettings) {
        firestore
            .collection(SETTINGS_COLLECTION)
            .document(NOTIFICATION_DOC)
            .set(
                mapOf(
                    "enabled" to settings.enabled,
                    "webhookUrl" to settings.webhookUrl,
                    "notifyTime" to settings.notifyTime,
                    "prefix" to settings.prefix,
                ),
            ).await()
    }
}
