package server.feeding

import com.google.cloud.firestore.Firestore
import model.FeedingSettings
import model.MealTime
import server.util.await

class FirestoreFeedingSettingsRepository(
    private val firestore: Firestore,
) : FeedingSettingsRepository {
    private val settingsDoc get() = firestore.collection("settings").document("feeding")

    override suspend fun getSettings(): FeedingSettings {
        val doc = settingsDoc.get().await()
        if (!doc.exists()) return FeedingSettings()
        val data = doc.data ?: return FeedingSettings()

        @Suppress("UNCHECKED_CAST")
        val mealOrderRaw = data["mealOrder"] as? List<String>
        val mealOrder =
            mealOrderRaw?.mapNotNull { name ->
                MealTime.entries.find { it.name == name }
            } ?: FeedingSettings().mealOrder

        @Suppress("UNCHECKED_CAST")
        val mealTimesRaw = data["mealTimes"] as? Map<String, String>
        val mealTimes =
            mealTimesRaw
                ?.mapNotNull { (key, value) ->
                    val meal = MealTime.entries.find { it.name == key }
                    meal?.let { it to value }
                }?.toMap() ?: FeedingSettings().mealTimes

        return FeedingSettings(
            mealOrder = mealOrder,
            mealTimes = mealTimes,
        )
    }

    override suspend fun updateSettings(settings: FeedingSettings) {
        settingsDoc
            .set(
                mapOf(
                    "mealOrder" to settings.mealOrder.map { it.name },
                    "mealTimes" to settings.mealTimes.map { (k, v) -> k.name to v }.toMap(),
                ),
            ).await()
    }
}
