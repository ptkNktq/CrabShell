package server.feeding

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import model.Feeding
import model.FeedingLog
import model.MealTime
import server.util.await
import java.util.concurrent.ConcurrentHashMap

class FirestoreFeedingRepository(
    private val firestore: Firestore,
) : FeedingRepository {
    private val cache = ConcurrentHashMap<String, FeedingLog>()

    override suspend fun getFeedingLog(
        petId: String,
        date: String,
    ): FeedingLog {
        val key = "$petId:$date"
        cache[key]?.let { return it }

        val doc =
            firestore
                .collection("pets")
                .document(petId)
                .collection("feeding_logs")
                .document(date)
                .get()
                .await()

        if (!doc.exists()) {
            val emptyLog = FeedingLog(date = date)
            cache[key] = emptyLog
            return emptyLog
        }

        val data = doc.data!!

        @Suppress("UNCHECKED_CAST")
        val feedingsRaw = data["feedings"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        val feedings =
            MealTime.entries.associateWith { meal ->
                val entry = feedingsRaw[meal.name.lowercase()]
                if (entry != null) {
                    Feeding(
                        done = entry["done"] as? Boolean ?: false,
                        timestamp = entry["timestamp"] as? String,
                    )
                } else {
                    Feeding()
                }
            }

        val log =
            FeedingLog(
                date = date,
                note = data["note"] as? String ?: "",
                feedings = feedings,
            )
        cache[key] = log
        return log
    }

    override suspend fun recordFeeding(
        petId: String,
        date: String,
        mealTime: MealTime,
        timestamp: String,
    ) {
        val docRef =
            firestore
                .collection("pets")
                .document(petId)
                .collection("feeding_logs")
                .document(date)

        docRef
            .set(
                mapOf(
                    "date" to date,
                    "feedings" to
                        mapOf(
                            mealTime.name.lowercase() to
                                mapOf(
                                    "done" to true,
                                    "timestamp" to timestamp,
                                ),
                        ),
                ),
                SetOptions.mergeFields("date", "feedings.${mealTime.name.lowercase()}"),
            ).await()

        refreshCache(petId, date)
    }

    override suspend fun updateTimestamp(
        petId: String,
        date: String,
        mealTime: MealTime,
        timestamp: String,
    ): Boolean {
        val log = getFeedingLog(petId, date)
        val feeding = log.feedings[mealTime]
        if (feeding == null || !feeding.done) return false

        val docRef =
            firestore
                .collection("pets")
                .document(petId)
                .collection("feeding_logs")
                .document(date)

        docRef
            .set(
                mapOf(
                    "feedings" to
                        mapOf(
                            mealTime.name.lowercase() to
                                mapOf("timestamp" to timestamp),
                        ),
                ),
                SetOptions.mergeFields("feedings.${mealTime.name.lowercase()}.timestamp"),
            ).await()

        refreshCache(petId, date)
        return true
    }

    override suspend fun updateNote(
        petId: String,
        date: String,
        note: String,
    ) {
        val docRef =
            firestore
                .collection("pets")
                .document(petId)
                .collection("feeding_logs")
                .document(date)

        docRef
            .set(
                mapOf("date" to date, "note" to note),
                SetOptions.mergeFields("date", "note"),
            ).await()

        refreshCache(petId, date)
    }

    private suspend fun refreshCache(
        petId: String,
        date: String,
    ) {
        val key = "$petId:$date"
        cache.remove(key)
        getFeedingLog(petId, date)
    }
}
