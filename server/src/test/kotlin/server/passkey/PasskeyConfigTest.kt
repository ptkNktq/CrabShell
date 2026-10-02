package server.passkey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasskeyConfigTest {
    @Test
    fun parseTrimsRpIdAndOrigins() {
        val config = PasskeyConfig.parse(rpId = "  example.com ", origins = " https://a.example.com , ,https://b.example.com ")

        assertEquals("example.com", config.rpId)
        assertTrue("https://a.example.com" in config.allowedOrigins)
        assertTrue("https://b.example.com" in config.allowedOrigins)
        assertFalse("" in config.allowedOrigins)
        assertTrue(config.enabled)
    }

    @Test
    fun parseTreatsNullAsUnset() {
        val config = PasskeyConfig.parse(rpId = null, origins = null)

        assertFalse(config.enabled)
    }

    @Test
    fun parseTreatsEmptyOrBlankAsUnset() {
        assertFalse(PasskeyConfig.parse(rpId = "", origins = "https://a.example.com").enabled)
        assertFalse(PasskeyConfig.parse(rpId = "   ", origins = "https://a.example.com").enabled)
        assertFalse(PasskeyConfig.parse(rpId = "example.com", origins = "").enabled)
        assertFalse(PasskeyConfig.parse(rpId = "example.com", origins = " , , ").enabled)
    }

    @Test
    fun parseRequiresBothRpIdAndOrigins() {
        assertFalse(PasskeyConfig.parse(rpId = "example.com", origins = null).enabled)
        assertFalse(PasskeyConfig.parse(rpId = null, origins = "https://a.example.com").enabled)
    }
}
