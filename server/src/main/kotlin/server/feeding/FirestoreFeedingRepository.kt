package server.feeding

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import model.Feeding
import model.FeedingLog
import model.MealTime
import server.util.await

class FirestoreFeedingRepository(
    private val firestore: Firestore,
) : FeedingRepository {
    override suspend fun getFeedingLog(
        petId: String,
        date: String,
    ): FeedingLog {
        val doc =
            firestore
                .collection("pets")
                .document(petId)
                .collection("feeding_logs")
                .document(date)
                .get()
                .await()

        if (!doc.exists()) return FeedingLog(date = date)

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

        return FeedingLog(
            date = date,
            note = data["note"] as? String ?: "",
            feedings = feedings,
        )
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
    }

    override suspend fun updateTimestamp(
        petId: String,
        date: String,
        mealTime: MealTime,
        timestamp: String,
    ): Boolean {
        val docRef =
            firestore
                .collection("pets")
                .document(petId)
                .collection("feeding_logs")
                .document(date)

        val doc = docRef.get().await()
        if (!doc.exists()) return false

        @Suppress("UNCHECKED_CAST")
        val feedingsRaw = doc.data?.get("feedings") as? Map<String, Map<String, Any?>>
        val entry = feedingsRaw?.get(mealTime.name.lowercase())
        val done = entry?.get("done") as? Boolean ?: false
        if (!done) return false

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
    }
}
