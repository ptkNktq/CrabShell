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

/** 給餌メモの入力ルール。サーバー（REST・MCP）とクライアントで共用する。 */
object FeedingNote {
    /** 1 日分のメモの上限文字数（Unicode のコードポイント数） */
    const val MAX_LENGTH = 100

    fun isValid(note: String): Boolean = note.codePointLength() <= MAX_LENGTH
}
