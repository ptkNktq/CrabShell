package server.mcp

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpAuthFailureGuardTest {
    private var now = Instant.parse("2026-03-14T00:00:00Z")
    private val guard = McpAuthFailureGuard { now }

    private fun fail(
        ip: String,
        times: Int,
    ) = repeat(times) { guard.recordFailure(ip) }

    @Test
    fun blocksOnlyAfterExceedingMaxFailuresWithinWindow() {
        fail("1.1.1.1", McpAuthFailureGuard.MAX_FAILURES)
        assertNull(guard.remainingBlock("1.1.1.1"))

        fail("1.1.1.1", 1)
        assertEquals(McpAuthFailureGuard.BLOCK_DURATION, guard.remainingBlock("1.1.1.1"))
        // ほかの IP には影響しない
        assertNull(guard.remainingBlock("2.2.2.2"))
    }

    @Test
    fun blockIsLiftedAfterBlockDuration() {
        fail("1.1.1.1", McpAuthFailureGuard.MAX_FAILURES + 1)

        now += McpAuthFailureGuard.BLOCK_DURATION - Duration.ofSeconds(1)
        assertEquals(Duration.ofSeconds(1), guard.remainingBlock("1.1.1.1"))

        now += Duration.ofSeconds(1)
        assertNull(guard.remainingBlock("1.1.1.1"))
    }

    @Test
    fun failuresInDifferentWindowsAreNotAccumulated() {
        fail("1.1.1.1", McpAuthFailureGuard.MAX_FAILURES)

        now += McpAuthFailureGuard.FAILURE_WINDOW
        fail("1.1.1.1", McpAuthFailureGuard.MAX_FAILURES)

        assertNull(guard.remainingBlock("1.1.1.1"))
    }
}
