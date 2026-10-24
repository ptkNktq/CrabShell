package server.util

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * キーごとに失敗を数え、[window] の間に [maxFailures] 回を超えて失敗したキーを [blockDuration] の間ブロックする。
 *
 * - 状態はメモリだけに持つ。サーバーを再起動するとブロックは解除される
 * - 大量のキーで失敗させられてもメモリが増え続けないよう、記録中とブロック中の合計が [maxTrackedKeys] に達したら新しいキーは記録しない
 *
 * 失敗の記録とブロック状態をメモリに保持するため、使う側はアプリ全体で 1 インスタンスを共有すること。
 */
class FailureBlocker(
    private val maxFailures: Int,
    private val window: Duration,
    private val blockDuration: Duration,
    private val maxTrackedKeys: Int,
    private val now: () -> Instant = Instant::now,
) {
    private data class FailureWindow(
        val startedAt: Instant,
        val count: Int,
    )

    private val failures = ConcurrentHashMap<String, FailureWindow>()
    private val blockedUntil = ConcurrentHashMap<String, Instant>()
    private val lastCleanupAt = AtomicReference(Instant.EPOCH)

    /** @return [key] がブロック中なら解除までの残り時間。ブロックされていなければ null */
    fun remainingBlock(key: String): Duration? {
        val until = blockedUntil[key] ?: return null
        val current = now()
        if (current >= until) {
            blockedUntil.remove(key, until)
            return null
        }
        return Duration.between(current, until)
    }

    /**
     * [key] の失敗を記録する。失敗が続いた場合はブロックを始める。
     * @return この記録でブロックを始めた場合は true
     */
    fun recordFailure(key: String): Boolean {
        val current = now()
        cleanupIfDue(current)
        if (failures.size + blockedUntil.size >= maxTrackedKeys && !failures.containsKey(key)) return false

        var blocked = false
        failures.compute(key) { _, window ->
            val next =
                if (window == null || current >= window.startedAt + this.window) {
                    FailureWindow(startedAt = current, count = 1)
                } else {
                    window.copy(count = window.count + 1)
                }
            if (next.count > maxFailures) {
                blocked = true
                null
            } else {
                next
            }
        }
        if (blocked) blockedUntil[key] = current + blockDuration
        return blocked
    }

    /** 期限の切れた記録を消す。リクエストごとに全件を走査しないよう、[CLEANUP_INTERVAL] に 1 回だけ行う */
    private fun cleanupIfDue(current: Instant) {
        val last = lastCleanupAt.get()
        if (current < last + CLEANUP_INTERVAL || !lastCleanupAt.compareAndSet(last, current)) return
        failures.entries.removeIf { current >= it.value.startedAt + window }
        blockedUntil.entries.removeIf { current >= it.value }
    }

    private companion object {
        val CLEANUP_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
