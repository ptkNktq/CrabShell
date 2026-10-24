package server.mcp

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpDisabledConnectionGuardTest {
    private var now = Instant.parse("2026-10-24T00:00:00Z")
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

    @Test
    fun blockIsLiftedAfterBlockDuration() {
        reject("uid1", McpDisabledConnectionGuard.MAX_REJECTIONS + 1)

        now += McpDisabledConnectionGuard.BLOCK_DURATION - Duration.ofSeconds(1)
        assertEquals(Duration.ofSeconds(1), guard.remainingBlock("uid1"))

        now += Duration.ofSeconds(1)
        assertNull(guard.remainingBlock("uid1"))
    }

    @Test
    fun rejectionsInDifferentWindowsAreNotAccumulated() {
        reject("uid1", McpDisabledConnectionGuard.MAX_REJECTIONS)

        now += McpDisabledConnectionGuard.REJECTION_WINDOW
        reject("uid1", McpDisabledConnectionGuard.MAX_REJECTIONS)

        assertNull(guard.remainingBlock("uid1"))
    }
}
