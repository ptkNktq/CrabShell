package model

import kotlinx.serialization.Serializable

@Serializable
enum class GarbageType { BURNABLE, NON_BURNABLE, RECYCLABLE }

@Serializable
enum class CollectionFrequency { WEEKLY, WEEK_1_3, WEEK_2_4 }

@Serializable
data class GarbageTypeSchedule(
    val garbageType: GarbageType,
    val daysOfWeek: List<Int>,
    val frequency: CollectionFrequency = CollectionFrequency.WEEKLY,
)

@Serializable
data class GarbageNotificationSettings(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    val notifyHour: Int = 10,
    val prefix: String = "",
)

/** 指定された曜日・月内週に該当するゴミ種を返す */
fun resolveGarbageTypes(
    schedules: List<GarbageTypeSchedule>,
    dayOfWeek: Int,
    weekOfMonth: Int,
): List<GarbageType> =
    schedules
        .filter { schedule ->
            dayOfWeek in schedule.daysOfWeek && matchesFrequency(schedule.frequency, weekOfMonth)
        }.map { it.garbageType }

private fun matchesFrequency(
    frequency: CollectionFrequency,
    weekOfMonth: Int,
): Boolean =
    when (frequency) {
        CollectionFrequency.WEEKLY -> true
        CollectionFrequency.WEEK_1_3 -> weekOfMonth == 1 || weekOfMonth == 3
        CollectionFrequency.WEEK_2_4 -> weekOfMonth == 2 || weekOfMonth == 4
    }
