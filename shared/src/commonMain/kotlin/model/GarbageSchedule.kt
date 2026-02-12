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
