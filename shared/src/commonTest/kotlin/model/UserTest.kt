package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserTest {
    private val json = Json

    @Test
    fun userRoundTrip() {
        val user = User(uid = "u1", email = "a@b.com", displayName = "Alice", isAdmin = true)
        val encoded = json.encodeToString(User.serializer(), user)
        val decoded = json.decodeFromString(User.serializer(), encoded)
        assertEquals(user, decoded)
    }

    @Test
    fun userDefaultValues() {
        val jsonStr = """{"uid":"u2","email":"b@c.com"}"""
        val decoded = json.decodeFromString(User.serializer(), jsonStr)
        assertNull(decoded.displayName)
        assertEquals(false, decoded.isAdmin)
    }

    @Test
    fun updateDisplayNameRequestRoundTrip() {
        val req = UpdateDisplayNameRequest(displayName = "Bob")
        val encoded = json.encodeToString(UpdateDisplayNameRequest.serializer(), req)
        val decoded = json.decodeFromString(UpdateDisplayNameRequest.serializer(), encoded)
        assertEquals(req, decoded)
    }
}
