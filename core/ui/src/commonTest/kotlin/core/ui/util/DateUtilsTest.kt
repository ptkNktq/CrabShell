package core.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DateUtilsTest {
    @Test
    fun shiftDate_crossesMonthBoundary() {
        assertEquals("2026-02-01", shiftDate("2026-01-31", 1))
        assertEquals("2026-01-31", shiftDate("2026-02-01", -1))
    }

    @Test
    fun shiftDate_crossesYearBoundary() {
        assertEquals("2027-01-01", shiftDate("2026-12-31", 1))
        assertEquals("2026-12-31", shiftDate("2027-01-01", -1))
    }

    @Test
    fun shiftDate_leapYearFebruary() {
        assertEquals("2024-02-29", shiftDate("2024-02-28", 1))
        assertEquals("2024-03-01", shiftDate("2024-02-29", 1))
        assertEquals("2025-03-01", shiftDate("2025-02-28", 1))
    }

    @Test
    fun daysInMonth_knownValues() {
        assertEquals(31, daysInMonth(2026, 1))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(30, daysInMonth(2026, 4))
        assertEquals(31, daysInMonth(2026, 12))
    }

    @Test
    fun firstDayOfWeek_knownValues() {
        // 2026-02-01 is a Sunday
        assertEquals(0, firstDayOfWeek(2026, 2))
        // 2026-03-01 is a Sunday
        assertEquals(0, firstDayOfWeek(2026, 3))
        // 2026-01-01 is a Thursday
        assertEquals(4, firstDayOfWeek(2026, 1))
    }

    @Test
    fun dayOfWeekShort_knownDates() {
        // 2026-01-01 is a Thursday
        assertEquals("木", dayOfWeekShort("2026-01-01"))
        // 2026-02-01 is a Sunday
        assertEquals("日", dayOfWeekShort("2026-02-01"))
    }

    @Test
    fun remainingTime_pastDeadlineIsExpired() {
        val now = Instant.parse("2026-07-25T00:00:00Z")
        assertEquals("期限切れ", remainingTime("2000-01-01", now))
    }

    @Test
    fun remainingTime_withinADayUsesHours() {
        // now = 2026-07-25 09:00 JST, deadline = 2026-07-25 11:00 JST -> 2時間後
        val now = Instant.parse("2026-07-25T00:00:00Z")
        assertEquals("あと2時間", remainingTime("2026-07-25 11:00", now))
    }

    @Test
    fun remainingTime_overADayUsesDays() {
        // now = 2026-07-25 09:00 JST, deadline = 2026-07-27 09:00 JST -> 2日後
        val now = Instant.parse("2026-07-25T00:00:00Z")
        assertEquals("あと2日", remainingTime("2026-07-27 09:00", now))
    }

    @Test
    fun dateWithHourBoundary_beforeDefaultBoundaryStaysOnPreviousDay() {
        // 2026-07-25 04:59 JST = 2026-07-24 19:59 UTC
        val now = Instant.parse("2026-07-24T19:59:00Z")
        assertEquals("2026-07-24", dateWithHourBoundary(now))
    }

    @Test
    fun dateWithHourBoundary_atDefaultBoundaryAdvancesToNewDay() {
        // 2026-07-25 05:00 JST = 2026-07-24 20:00 UTC
        val now = Instant.parse("2026-07-24T20:00:00Z")
        assertEquals("2026-07-25", dateWithHourBoundary(now))
    }

    @Test
    fun dateWithHourBoundary_respectsCustomBoundaryHour() {
        // 2026-07-25 09:59 JST, boundaryHour=10 -> まだ前日扱い
        val now = Instant.parse("2026-07-25T00:59:00Z")
        assertEquals("2026-07-24", dateWithHourBoundary(now, boundaryHour = 10))
        // 2026-07-25 10:00 JST, boundaryHour=10 -> 当日
        val atBoundary = Instant.parse("2026-07-25T01:00:00Z")
        assertEquals("2026-07-25", dateWithHourBoundary(atBoundary, boundaryHour = 10))
    }

    @Test
    fun todayDate_usesJstNotUtc() {
        // 2026-07-25 00:30 JST = 2026-07-24 15:30 UTC, still "07-25" in JST despite being "07-24" in UTC
        val now = Instant.parse("2026-07-24T15:30:00Z")
        assertEquals("2026-07-25", todayDate(now))
    }

    @Test
    fun currentTime_convertsUtcToJst() {
        val now = Instant.parse("2026-07-25T00:30:00Z")
        assertEquals("09:30", currentTime(now))
    }

    @Test
    fun toJstHHMM_convertsIsoTimestamp() {
        assertEquals("09:30", toJstHHMM("2026-07-25T00:30:00Z"))
        assertEquals("09", toJstHour("2026-07-25T00:30:00Z"))
        assertEquals("30", toJstMinute("2026-07-25T00:30:00Z"))
    }

    @Test
    fun formattedToday_usesHalfWidthParentheses() {
        // 2026-07-25 09:30 JST is a Saturday
        val now = Instant.parse("2026-07-25T00:30:00Z")
        assertEquals("7月25日(土)", formattedToday(now))
    }
}
