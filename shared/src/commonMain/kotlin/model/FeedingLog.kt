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
