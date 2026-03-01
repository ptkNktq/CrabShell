package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // --- resolveGarbageTypes テスト ---

    private val schedules =
        listOf(
            GarbageTypeSchedule(GarbageType.BURNABLE, listOf(1, 4)), // 月・木、毎週
            GarbageTypeSchedule(GarbageType.NON_BURNABLE, listOf(3), CollectionFrequency.WEEK_1_3), // 水、第1・3週
            GarbageTypeSchedule(GarbageType.RECYCLABLE, listOf(3), CollectionFrequency.WEEK_2_4), // 水、第2・4週
        )

    @Test
    fun resolveWeeklyMatchesDayOfWeek() {
        // 月曜(1)・第1週 → 可燃のみ
        assertEquals(listOf(GarbageType.BURNABLE), resolveGarbageTypes(schedules, dayOfWeek = 1, weekOfMonth = 1))
    }

    @Test
    fun resolveWeek13MatchesWeek1() {
        // 水曜(3)・第1週 → 不燃（WEEK_1_3）
        assertEquals(listOf(GarbageType.NON_BURNABLE), resolveGarbageTypes(schedules, dayOfWeek = 3, weekOfMonth = 1))
    }

    @Test
    fun resolveWeek13MatchesWeek3() {
        // 水曜(3)・第3週 → 不燃（WEEK_1_3）
        assertEquals(listOf(GarbageType.NON_BURNABLE), resolveGarbageTypes(schedules, dayOfWeek = 3, weekOfMonth = 3))
    }

    @Test
    fun resolveWeek24MatchesWeek2() {
        // 水曜(3)・第2週 → 資源（WEEK_2_4）
        assertEquals(listOf(GarbageType.RECYCLABLE), resolveGarbageTypes(schedules, dayOfWeek = 3, weekOfMonth = 2))
    }

    @Test
    fun resolveWeek24MatchesWeek4() {
        // 水曜(3)・第4週 → 資源（WEEK_2_4）
        assertEquals(listOf(GarbageType.RECYCLABLE), resolveGarbageTypes(schedules, dayOfWeek = 3, weekOfMonth = 4))
    }

    @Test
    fun resolveNoMatchReturnsEmpty() {
        // 日曜(0) → 該当なし
        assertTrue(resolveGarbageTypes(schedules, dayOfWeek = 0, weekOfMonth = 1).isEmpty())
    }

    @Test
    fun resolveWeek5NoFrequencyMatch() {
        // 水曜(3)・第5週 → WEEK_1_3 も WEEK_2_4 も該当しない
        assertTrue(resolveGarbageTypes(schedules, dayOfWeek = 3, weekOfMonth = 5).isEmpty())
    }

    @Test
    fun resolveMultipleMatches() {
        // 複数のスケジュールが同じ曜日にマッチするケース
        val overlapping =
            listOf(
                GarbageTypeSchedule(GarbageType.BURNABLE, listOf(1)),
                GarbageTypeSchedule(GarbageType.RECYCLABLE, listOf(1)),
            )
        assertEquals(
            listOf(GarbageType.BURNABLE, GarbageType.RECYCLABLE),
            resolveGarbageTypes(overlapping, dayOfWeek = 1, weekOfMonth = 1),
        )
    }

    @Test
    fun resolveEmptySchedules() {
        assertTrue(resolveGarbageTypes(emptyList(), dayOfWeek = 1, weekOfMonth = 1).isEmpty())
    }
}
