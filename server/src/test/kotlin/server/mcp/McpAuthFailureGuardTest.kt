package server.mcp

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpAuthFailureGuardTest {
    private var now = Instant.parse("2026-03-14T00:00:00Z")
    private val guard = McpAuthFailureGuard(now = { now })

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

    @Test
    fun ipv6AddressesInSamePrefixAreCountedTogether() {
        repeat(McpAuthFailureGuard.MAX_FAILURES + 1) { i -> guard.recordFailure("2001:db8:1:2::${i + 1}") }

        assertEquals(McpAuthFailureGuard.BLOCK_DURATION, guard.remainingBlock("2001:db8:1:2:ffff::1"))
        assertNull(guard.remainingBlock("2001:db8:1:3::1"))
    }

    @Test
    fun guardKeyRoundsOnlyIpv6() {
        assertEquals("2001:db8:1:2::/64", McpAuthFailureGuard.guardKey("2001:db8:1:2:3:4:5:6"))
        assertEquals("203.0.113.1", McpAuthFailureGuard.guardKey("203.0.113.1"))
        assertEquals("localhost", McpAuthFailureGuard.guardKey("localhost"))
    }

    @Test
    fun newIpsAreNotTrackedOnceTrackedAndBlockedReachLimit() {
        val guard = McpAuthFailureGuard(now = { now }, maxTrackedIps = 2)
        repeat(McpAuthFailureGuard.MAX_FAILURES + 1) { guard.recordFailure("1.1.1.1") }
        guard.recordFailure("2.2.2.2")

        // ブロック中 1 件 + 記録中 1 件で上限に達しているため、新しい IP は数えない
        repeat(McpAuthFailureGuard.MAX_FAILURES + 1) { guard.recordFailure("3.3.3.3") }

        assertNull(guard.remainingBlock("3.3.3.3"))
        // すでに記録中の IP は数え続ける
        repeat(McpAuthFailureGuard.MAX_FAILURES) { guard.recordFailure("2.2.2.2") }
        assertEquals(McpAuthFailureGuard.BLOCK_DURATION, guard.remainingBlock("2.2.2.2"))
    }
}
