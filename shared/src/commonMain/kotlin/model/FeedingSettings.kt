package model

import kotlinx.serialization.Serializable

@Serializable
data class FeedingSettings(
    val mealOrder: List<MealTime> = listOf(MealTime.LUNCH, MealTime.EVENING, MealTime.MORNING),
    val mealTimes: Map<MealTime, String> =
        mapOf(
            MealTime.MORNING to "07:00",
            MealTime.LUNCH to "12:00",
            MealTime.EVENING to "18:00",
        ),
    val reminderEnabled: Boolean = false,
    val reminderDelayMinutes: Int = 30,
    val reminderPrefix: String = "",
    val reminderWebhookUrl: String = "",
    val reminderBaseUrl: String = "",
)
