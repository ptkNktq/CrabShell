package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class McpAuthorizationTest {
    private val json = Json

    @Test
    fun completeRequestRoundTrip() {
        val request = McpAuthorizationCompleteRequest(externalAuthId = "01J3X4Y5Z6")
        val encoded = json.encodeToString(McpAuthorizationCompleteRequest.serializer(), request)
        assertEquals(request, json.decodeFromString(McpAuthorizationCompleteRequest.serializer(), encoded))
    }

    @Test
    fun completeResponseRoundTrip() {
        val response = McpAuthorizationCompleteResponse(redirectUri = "https://example.authkit.app/consent")
        val encoded = json.encodeToString(McpAuthorizationCompleteResponse.serializer(), response)
        assertEquals(response, json.decodeFromString(McpAuthorizationCompleteResponse.serializer(), encoded))
    }
}
