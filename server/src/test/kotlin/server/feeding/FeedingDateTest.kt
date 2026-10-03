package server.feeding

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedingDateTest {
    private val jst = ZoneId.of("Asia/Tokyo")

    @Test
    fun before5amReturnsPreviousDay() {
        val jstNow = ZonedDateTime.of(2026, 3, 14, 4, 59, 0, 0, jst)
        assertEquals("2026-03-13", feedingDate(jstNow))
    }

    @Test
    fun at5amReturnsCurrentDay() {
        val jstNow = ZonedDateTime.of(2026, 3, 14, 5, 0, 0, 0, jst)
        assertEquals("2026-03-14", feedingDate(jstNow))
    }

    @Test
    fun instantIsConvertedToJst() {
        // UTC 2026-03-13 20:00 = JST 2026-03-14 05:00
        assertEquals("2026-03-14", feedingDate(Instant.parse("2026-03-13T20:00:00Z")))
        // UTC 2026-03-13 19:59 = JST 2026-03-14 04:59
        assertEquals("2026-03-13", feedingDate(Instant.parse("2026-03-13T19:59:00Z")))
    }
}
