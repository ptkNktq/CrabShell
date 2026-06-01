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

/** `PUT /feeding/{petId}/{date}/note` のレスポンス DTO（編集後の値を echo）。 */
@Serializable
data class FeedingNoteResponse(
    val note: String,
)

/** `POST /feeding/test-scheduled` / `POST /feeding/test-reminder` のレスポンス DTO（送信成功通知）。 */
@Serializable
data class FeedingTestNotificationResponse(
    val status: String,
)
