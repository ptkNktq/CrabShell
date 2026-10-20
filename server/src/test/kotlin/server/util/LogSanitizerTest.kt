package server.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogSanitizerTest {
    @Test
    fun valueForLogHasNoControlCharactersAndIsTruncated() {
        assertEquals("uid1?a?b", sanitizeForLog("uid1\na\u2028b"))
        assertEquals(128, sanitizeForLog("a".repeat(1000))!!.length)
        assertNull(sanitizeForLog(null))
    }

    @Test
    fun truncatesWithoutSplittingSurrogatePair() {
        // 😺 は UTF-16 で 2 コード単位。UTF-16 単位で切ると 128 番目で対が分かれる
        val value = "a" + "😺".repeat(200)

        val sanitized = sanitizeForLog(value)!!

        assertEquals(128, sanitized.codePointCount(0, sanitized.length))
        assertEquals("a" + "😺".repeat(127), sanitized)
    }
}
