package model

import kotlinx.serialization.Serializable

@Serializable
enum class MealTime { MORNING, LUNCH, EVENING }

@Serializable
data class Feeding(
    val done: Boolean = false,
    val timestamp: String? = null,
)

@Serializable
data class FeedingLog(
    val date: String,
    val note: String = "",
    val feedings: Map<MealTime, Feeding> = MealTime.entries.associateWith { Feeding() },
)

/** `PATCH /feeding/{petId}/{date}/{mealTime}/timestamp` のリクエスト DTO。 */
@Serializable
data class FeedingTimestampUpdateRequest(
    val timestamp: String,
)

/** `PUT /feeding/{petId}/{date}/note` のリクエスト DTO。 */
@Serializable
data class FeedingNoteUpdateRequest(
    val note: String,
)
