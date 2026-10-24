package server.mcp

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 窓・解除・記録数の上限は FailureBlockerTest で確かめる。ここではしきい値と、IP を数える単位を確かめる
class McpAuthFailureGuardTest {
    private val now = Instant.parse("2026-03-14T00:00:00Z")
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
        // IPv6 リテラルとして解釈できない値は、名前解決をせずそのまま使う
        assertEquals("g::1", McpAuthFailureGuard.guardKey("g::1"))
    }
}
