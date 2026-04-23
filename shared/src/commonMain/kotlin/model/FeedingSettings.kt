package model

import kotlinx.serialization.Serializable

@Serializable
data class FeedingSettings(
    val mealOrder: List<MealTime> = DEFAULT_MEAL_ORDER,
    val mealTimes: Map<MealTime, String> =
        mapOf(
            MealTime.MORNING to "07:00",
            MealTime.LUNCH to "12:00",
            MealTime.EVENING to "18:00",
        ),
    val reminderEnabled: Boolean = false,
    val reminderWebhookUrl: String = "",
    val reminderDelayMinutes: Int = 30,
    val reminderPrefix: String = "",
) {
    companion object {
        val DEFAULT_MEAL_ORDER: List<MealTime> = listOf(MealTime.LUNCH, MealTime.EVENING, MealTime.MORNING)
    }
}
