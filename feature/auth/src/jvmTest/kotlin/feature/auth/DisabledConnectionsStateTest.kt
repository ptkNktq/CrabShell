package feature.auth

import model.McpConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisabledConnectionsStateTest {
    private fun connections(vararg names: String) =
        names.mapIndexed { i, name ->
            McpConnection(
                id = "c$i",
                clientName = name,
                firstUsedAt = "2026-09-01T00:00:00Z",
                lastUsedAt = "2026-10-01T00:00:00Z",
                disabledAt = "2026-10-01T00:00:00Z",
            )
        }

    private val many = connections(*Array(DISABLED_FILTER_THRESHOLD + 1) { if (it % 2 == 0) "Claude $it" else "Cursor $it" })

    @Test
    fun fewConnectionsAreAllShownWithoutToggle() {
        val state = DisabledConnectionsState(connections = connections("A", "B", "C"))

        assertEquals(3, state.visibleConnections.size)
        assertEquals(0, state.hiddenCount)
        assertFalse(state.showsFilter)
    }

    @Test
    fun collapsedShowsOnlyFirstConnections() {
        val state = DisabledConnectionsState(connections = many)

        assertEquals(many.take(DISABLED_COLLAPSED_COUNT), state.visibleConnections)
        assertEquals(many.size - DISABLED_COLLAPSED_COUNT, state.hiddenCount)
        // 畳んでいる間は絞り込み欄を出さず、残っている文字でも絞り込まない
        assertFalse(state.copy(filterQuery = "Cursor").showsFilter)
        assertEquals(many.take(DISABLED_COLLAPSED_COUNT), state.copy(filterQuery = "Cursor").visibleConnections)
    }

    @Test
    fun expandedShowsFilterOnlyWhenMany() {
        assertTrue(DisabledConnectionsState(connections = many, isExpanded = true).showsFilter)
        assertFalse(DisabledConnectionsState(connections = many.take(DISABLED_FILTER_THRESHOLD), isExpanded = true).showsFilter)
    }

    @Test
    fun filterMatchesNameIgnoringCaseAndSpaces() {
        val state = DisabledConnectionsState(connections = many, isExpanded = true, filterQuery = "  claude ")

        assertTrue(state.visibleConnections.isNotEmpty())
        assertTrue(state.visibleConnections.all { it.clientName.startsWith("Claude") })
        assertEquals(many, state.copy(filterQuery = " ").visibleConnections)
    }
}
