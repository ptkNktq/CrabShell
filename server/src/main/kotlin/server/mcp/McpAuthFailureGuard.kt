package server.mcp

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory
import java.net.Inet6Address
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * `/mcp` で認証に失敗したリクエストを IP ごとに数え、短時間に失敗が続いた IP からのリクエストを一定時間受け付けないようにする。
 *
 * - 認証に成功したリクエストは数えない（正規の利用を制限しない）
 * - [FAILURE_WINDOW] の間に [MAX_FAILURES] 回を超えて失敗した IP を [BLOCK_DURATION] の間ブロックする。
 *   ブロック中は正しいトークンでも受け付けない
 * - IPv6 は /64 単位で数える。利用者は通常 /64 以上を割り当てられており、アドレスを替えるだけで数え直しにさせないため
 * - 状態はメモリだけに持つ。サーバーを再起動するとブロックは解除される
 *
 * 失敗の記録とブロック状態をメモリに保持するため、アプリ全体で 1 インスタンスを共有すること（Koin の single で登録）。
 */
class McpAuthFailureGuard(
    private val now: () -> Instant = Instant::now,
    private val maxTrackedIps: Int = MAX_TRACKED_IPS,
) {
    private val logger = LoggerFactory.getLogger(McpAuthFailureGuard::class.java)

    private data class FailureWindow(
        val startedAt: Instant,
        val count: Int,
    )

    private val failures = ConcurrentHashMap<String, FailureWindow>()
    private val blockedUntil = ConcurrentHashMap<String, Instant>()
    private val lastCleanupAt = AtomicReference(Instant.EPOCH)

    /** @return [ip] がブロック中なら解除までの残り時間。ブロックされていなければ null */
    fun remainingBlock(ip: String): Duration? {
        val key = guardKey(ip)
        val until = blockedUntil[key] ?: return null
        val current = now()
        if (current >= until) {
            blockedUntil.remove(key, until)
            return null
        }
        return Duration.between(current, until)
    }

    /** [ip] からのリクエストが認証に失敗したことを記録する。失敗が続いた場合はブロックを始める */
    fun recordFailure(ip: String) {
        val key = guardKey(ip)
        val current = now()
        cleanupIfDue(current)
        // 大量の IP から失敗させられてもメモリが増え続けないよう、記録中とブロック中の合計が上限に達したら新しい IP は記録しない
        if (failures.size + blockedUntil.size >= maxTrackedIps && !failures.containsKey(key)) return

        var blocked = false
        failures.compute(key) { _, window ->
            val next =
                if (window == null || current >= window.startedAt + FAILURE_WINDOW) {
                    FailureWindow(startedAt = current, count = 1)
                } else {
                    window.copy(count = window.count + 1)
                }
            if (next.count > MAX_FAILURES) {
                blocked = true
                null
            } else {
                next
            }
        }
        if (blocked) {
            blockedUntil[key] = current + BLOCK_DURATION
            logger.warn("MCP requests from {} blocked for {} after repeated authentication failures", key, BLOCK_DURATION)
        }
    }

    /** 期限の切れた記録を消す。リクエストごとに全件を走査しないよう、[CLEANUP_INTERVAL] に 1 回だけ行う */
    private fun cleanupIfDue(current: Instant) {
        val last = lastCleanupAt.get()
        if (current < last + CLEANUP_INTERVAL || !lastCleanupAt.compareAndSet(last, current)) return
        failures.entries.removeIf { current >= it.value.startedAt + FAILURE_WINDOW }
        blockedUntil.entries.removeIf { current >= it.value }
    }

    companion object {
        internal const val MAX_FAILURES = 10

        /** 数える単位のキー。IPv6 は先頭 64 ビット（/64）に丸め、IPv4 やアドレスとして解釈できない値はそのまま使う */
        internal fun guardKey(ip: String): String {
            // IP リテラルだけを解釈する（ホスト名を渡されても DNS を引かない）
            if (':' !in ip) return ip
            val address = runCatching { InetAddress.getByName(ip) }.getOrNull() as? Inet6Address ?: return ip
            val bytes = address.address
            // 先頭 8 バイトを 16 ビットずつ 4 グループにする
            val groups = (0 until 4).map { i -> ((bytes[2 * i].toInt() and 0xff) shl 8) or (bytes[2 * i + 1].toInt() and 0xff) }
            return groups.joinToString(":") { "%x".format(it) } + "::/64"
        }

        internal val FAILURE_WINDOW: Duration = Duration.ofMinutes(1)
        internal val BLOCK_DURATION: Duration = Duration.ofMinutes(30)

        private val CLEANUP_INTERVAL = Duration.ofMinutes(1)
        private const val MAX_TRACKED_IPS = 10_000
    }
}

/** [McpAuthFailureGuard] でブロック中の IP からのリクエストを、認証より前に 429 で返すプラグイン */
class McpAuthFailureGuardPluginConfig {
    lateinit var guard: McpAuthFailureGuard
}

val McpAuthFailureGuardPlugin =
    createRouteScopedPlugin("McpAuthFailureGuard", ::McpAuthFailureGuardPluginConfig) {
        val guard = pluginConfig.guard
        onCall { call ->
            val remaining = guard.remainingBlock(call.request.origin.remoteAddress) ?: return@onCall
            // 1 秒未満の端数は切り上げる
            call.response.header(HttpHeaders.RetryAfter, (remaining.toMillis() + 999) / 1000)
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Too many requests"))
        }
    }
