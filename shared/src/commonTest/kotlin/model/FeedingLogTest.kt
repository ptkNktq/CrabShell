package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FeedingLogTest {
    private val json = Json

    @Test
    fun mealTimeEnumAllValues() {
        assertEquals(listOf(MealTime.MORNING, MealTime.LUNCH, MealTime.EVENING), MealTime.entries)
    }

    @Test
    fun feedingRoundTrip() {
        val feeding = Feeding(done = true, timestamp = "2024-01-01T08:00:00")
        val encoded = json.encodeToString(Feeding.serializer(), feeding)
        val decoded = json.decodeFromString(Feeding.serializer(), encoded)
        assertEquals(feeding, decoded)
    }

    @Test
    fun feedingDefaultValues() {
        val jsonStr = """{}"""
        val decoded = json.decodeFromString(Feeding.serializer(), jsonStr)
        assertFalse(decoded.done)
        assertNull(decoded.timestamp)
    }

    @Test
    fun feedingLogDefaultFeedings() {
        val jsonStr = """{"date":"2024-01-01"}"""
        val decoded = json.decodeFromString(FeedingLog.serializer(), jsonStr)
        assertEquals("2024-01-01", decoded.date)
        assertEquals("", decoded.note)
        assertEquals(3, decoded.feedings.size)
        for (meal in MealTime.entries) {
            assertFalse(decoded.feedings.getValue(meal).done)
        }
    }
}
