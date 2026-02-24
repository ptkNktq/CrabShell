package server.garbage

import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule
import server.firestore.FirestoreProvider
import server.util.await

private const val SETTINGS_COLLECTION = "settings"
private const val GARBAGE_DOC = "garbage_schedule"
private const val ENTRIES_FIELD = "entries"

/** ゴミ出しスケジュールの Firestore アクセスを集約するリポジトリ */
object GarbageRepository {
    private val firestore get() = FirestoreProvider.instance

    @Suppress("UNCHECKED_CAST")
    suspend fun getSchedules(): List<GarbageTypeSchedule> {
        val doc =
            firestore
                .collection(SETTINGS_COLLECTION)
                .document(GARBAGE_DOC)
                .get()
                .await()

        if (!doc.exists()) return emptyList()

        val entriesRaw = doc.get(ENTRIES_FIELD) as? List<Map<String, Any?>> ?: return emptyList()
        return entriesRaw.map { entry ->
            GarbageTypeSchedule(
                garbageType = GarbageType.valueOf(entry["garbageType"] as String),
                daysOfWeek = (entry["daysOfWeek"] as List<*>).map { (it as Long).toInt() },
                frequency =
                    CollectionFrequency.valueOf(
                        entry["frequency"] as? String ?: "WEEKLY",
                    ),
            )
        }
    }

    suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>) {
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
    }
}
