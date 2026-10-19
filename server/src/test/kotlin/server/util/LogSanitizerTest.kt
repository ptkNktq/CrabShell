package server.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogSanitizerTest {
    @Test
    fun valueForLogHasNoControlCharactersAndIsTruncated() {
        assertEquals("uid1?a?b", sanitizeForLog("uid1\na b"))
        assertEquals(128, sanitizeForLog("a".repeat(1000))!!.length)
        assertNull(sanitizeForLog(null))
    }
}
