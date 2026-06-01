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

/** `PATCH /api/pets/{petId}/feeding/{date}/{mealTime}/timestamp` のリクエスト DTO。 */
@Serializable
data class FeedingTimestampUpdateRequest(
    val timestamp: String,
)

/** `PUT /api/pets/{petId}/feeding/{date}/note` のリクエスト DTO。 */
@Serializable
data class FeedingNoteUpdateRequest(
    val note: String,
)

/** `PUT /api/pets/{petId}/feeding/{date}/note` のレスポンス DTO（編集後の値を echo）。 */
@Serializable
data class FeedingNoteResponse(
    val note: String,
)

/** `POST /api/feeding/test-scheduled` / `POST /api/feeding/test-reminder` のレスポンス DTO（送信成功通知）。 */
@Serializable
data class FeedingTestNotificationResponse(
    val status: String,
)
