package server.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpConfigTest {
    @Test
    fun parseDerivesUrlsFromAppUrlAndAuthKitDomain() {
        val config =
            McpConfig.parse(
                apiKey = " sk_test ",
                authKitDomain = " https://example.authkit.app/ ",
                appUrl = " https://crab.example.com/ ",
            )

        assertTrue(config.enabled)
        assertEquals("sk_test", config.apiKey)
        assertEquals("https://example.authkit.app", config.issuer)
        assertEquals("https://example.authkit.app/oauth2/jwks", config.jwksUrl)
        assertEquals("https://crab.example.com/mcp", config.resource)
        assertEquals("https://crab.example.com/.well-known/oauth-protected-resource/mcp", config.resourceMetadataUrl)
    }

    @Test
    fun parseAddsHttpsSchemeToAuthKitDomain() {
        val config = McpConfig.parse(apiKey = "sk_test", authKitDomain = "example.authkit.app", appUrl = "https://crab.example.com")

        assertEquals("https://example.authkit.app", config.issuer)
    }

    @Test
    fun parseTreatsNullOrBlankAsUnset() {
        assertFalse(McpConfig.parse(apiKey = null, authKitDomain = "example.authkit.app", appUrl = "https://crab.example.com").enabled)
        assertFalse(McpConfig.parse(apiKey = "sk_test", authKitDomain = "  ", appUrl = "https://crab.example.com").enabled)
        assertFalse(McpConfig.parse(apiKey = "sk_test", authKitDomain = "example.authkit.app", appUrl = "").enabled)
    }

    @Test
    fun parseTreatsHttpAuthKitDomainAsUnset() {
        // 署名検証の公開鍵（JWKS）を平文で取得しないよう、http は受け付けない
        listOf("http://example.authkit.app", "HTTP://example.authkit.app", "ftp://example.authkit.app", "https://").forEach { domain ->
            assertFalse(McpConfig.parse(apiKey = "sk_test", authKitDomain = domain, appUrl = "https://crab.example.com").enabled, domain)
        }
    }

    @Test
    fun parseTreatsInvalidAppUrlAsUnset() {
        assertFalse(McpConfig.parse(apiKey = "sk_test", authKitDomain = "example.authkit.app", appUrl = "crab.example.com").enabled)
        assertFalse(McpConfig.parse(apiKey = "sk_test", authKitDomain = "example.authkit.app", appUrl = "ftp://crab.example.com").enabled)
    }

    @Test
    fun toStringDoesNotContainApiKey() {
        val config = McpConfig.parse(apiKey = "sk_secret", authKitDomain = "example.authkit.app", appUrl = "https://crab.example.com")

        assertFalse("sk_secret" in config.toString())
    }
}
