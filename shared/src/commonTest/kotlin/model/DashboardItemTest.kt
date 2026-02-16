package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardItemTest {
    private val json = Json

    @Test
    fun dashboardItemRoundTrip() {
        val item = DashboardItem(id = 1, title = "Task", description = "Do something", status = Status.ACTIVE)
        val encoded = json.encodeToString(DashboardItem.serializer(), item)
        val decoded = json.decodeFromString(DashboardItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun dashboardItemDefaultStatus() {
        val jsonStr = """{"id":2,"title":"T","description":"D"}"""
        val decoded = json.decodeFromString(DashboardItem.serializer(), jsonStr)
        assertEquals(Status.ACTIVE, decoded.status)
    }

    @Test
    fun statusEnumAllValues() {
        val values = Status.entries
        assertEquals(listOf(Status.ACTIVE, Status.INACTIVE, Status.PENDING), values)
    }
}
