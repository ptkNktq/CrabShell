package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GarbageScheduleTest {
    private val json = Json

    @Test
    fun garbageTypeEnumAllValues() {
        assertEquals(
            listOf(GarbageType.BURNABLE, GarbageType.NON_BURNABLE, GarbageType.RECYCLABLE),
            GarbageType.entries,
        )
    }

    @Test
    fun collectionFrequencyEnumAllValues() {
        assertEquals(
            listOf(CollectionFrequency.WEEKLY, CollectionFrequency.WEEK_1_3, CollectionFrequency.WEEK_2_4),
            CollectionFrequency.entries,
        )
    }

    @Test
    fun garbageTypeScheduleRoundTripAndDefault() {
        val schedule =
            GarbageTypeSchedule(
                garbageType = GarbageType.BURNABLE,
                daysOfWeek = listOf(1, 4),
            )
        val encoded = json.encodeToString(GarbageTypeSchedule.serializer(), schedule)
        val decoded = json.decodeFromString(GarbageTypeSchedule.serializer(), encoded)
        assertEquals(schedule, decoded)
        assertEquals(CollectionFrequency.WEEKLY, decoded.frequency)
    }
}
