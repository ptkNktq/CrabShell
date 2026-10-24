package server.util

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FailureBlockerTest {
    private var now = Instant.parse("2026-10-25T00:00:00Z")
    private val window = Duration.ofMinutes(1)
    private val blockDuration = Duration.ofMinutes(30)

    private fun blocker(maxTrackedKeys: Int = 100) =
        FailureBlocker(maxFailures = 3, window = window, blockDuration = blockDuration, maxTrackedKeys = maxTrackedKeys, now = { now })

    @Test
    fun blocksOnlyAfterExceedingMaxFailuresWithinWindow() {
        val blocker = blocker()

        repeat(3) { assertFalse(blocker.recordFailure("a")) }
        assertNull(blocker.remainingBlock("a"))

        // ブロックを始めた記録でだけ true を返す（呼び出し側がログを 1 回だけ出せるように）
        assertTrue(blocker.recordFailure("a"))
        assertEquals(blockDuration, blocker.remainingBlock("a"))
        // ほかのキーには影響しない
        assertNull(blocker.remainingBlock("b"))
    }

    @Test
    fun blockIsLiftedAfterBlockDuration() {
        val blocker = blocker()
        repeat(4) { blocker.recordFailure("a") }

        now += blockDuration - Duration.ofSeconds(1)
        assertEquals(Duration.ofSeconds(1), blocker.remainingBlock("a"))

        now += Duration.ofSeconds(1)
        assertNull(blocker.remainingBlock("a"))
    }

    @Test
    fun failuresInDifferentWindowsAreNotAccumulated() {
        val blocker = blocker()
        repeat(3) { blocker.recordFailure("a") }

        now += window
        repeat(3) { blocker.recordFailure("a") }

        assertNull(blocker.remainingBlock("a"))
    }

    @Test
    fun newKeysAreNotTrackedOnceTrackedAndBlockedReachLimit() {
        val blocker = blocker(maxTrackedKeys = 2)
        repeat(4) { blocker.recordFailure("a") }
        blocker.recordFailure("b")

        // ブロック中 1 件 + 記録中 1 件で上限に達しているため、新しいキーは数えない
        repeat(4) { blocker.recordFailure("c") }

        assertNull(blocker.remainingBlock("c"))
        // すでに記録中のキーは数え続ける
        repeat(3) { blocker.recordFailure("b") }
        assertEquals(blockDuration, blocker.remainingBlock("b"))
    }
}
