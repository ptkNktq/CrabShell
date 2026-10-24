package server.mcp

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 窓・解除・記録数の上限は FailureBlockerTest で確かめる。ここではしきい値と、uid ごとに数えることを確かめる
class McpDisabledConnectionGuardTest {
    private val now = Instant.parse("2026-10-24T00:00:00Z")
    private val guard = McpDisabledConnectionGuard(now = { now })

    private fun reject(
        uid: String,
        times: Int,
    ) = repeat(times) { guard.recordRejection(uid) }

    @Test
    fun blocksOnlyAfterExceedingMaxRejectionsWithinWindow() {
        reject("uid1", McpDisabledConnectionGuard.MAX_REJECTIONS)
        assertNull(guard.remainingBlock("uid1"))

        reject("uid1", 1)
        assertEquals(McpDisabledConnectionGuard.BLOCK_DURATION, guard.remainingBlock("uid1"))
        // ほかのユーザーには影響しない
        assertNull(guard.remainingBlock("uid2"))
    }
}
