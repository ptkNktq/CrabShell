package server.mcp

import com.google.common.net.InetAddresses
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory
import server.util.FailureBlocker
import java.net.Inet6Address
import java.time.Duration
import java.time.Instant

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
    now: () -> Instant = Instant::now,
    maxTrackedIps: Int = MAX_TRACKED_IPS,
) {
    private val logger = LoggerFactory.getLogger(McpAuthFailureGuard::class.java)

    private val blocker =
        FailureBlocker(
            maxFailures = MAX_FAILURES,
            window = FAILURE_WINDOW,
            blockDuration = BLOCK_DURATION,
            maxTrackedKeys = maxTrackedIps,
            now = now,
        )

    /** @return [ip] がブロック中なら解除までの残り時間。ブロックされていなければ null */
    fun remainingBlock(ip: String): Duration? = blocker.remainingBlock(guardKey(ip))

    /** [ip] からのリクエストが認証に失敗したことを記録する。失敗が続いた場合はブロックを始める */
    fun recordFailure(ip: String) {
        val key = guardKey(ip)
        if (blocker.recordFailure(key)) {
            logger.warn("MCP requests from {} blocked for {} after repeated authentication failures", key, BLOCK_DURATION)
        }
    }

    companion object {
        internal const val MAX_FAILURES = 10

        /** 数える単位のキー。IPv6 は先頭 64 ビット（/64）に丸め、IPv4 やアドレスとして解釈できない値はそのまま使う */
        internal fun guardKey(ip: String): String {
            // IP リテラルだけを解釈する。InetAddress.getByName はリテラルとして解釈できない値で DNS を引くため使わない
            if (!InetAddresses.isInetAddress(ip)) return ip
            val address = InetAddresses.forString(ip) as? Inet6Address ?: return ip
            val bytes = address.address
            // 先頭 8 バイトを 16 ビットずつ 4 グループにする
            val groups = (0 until 4).map { i -> ((bytes[2 * i].toInt() and 0xff) shl 8) or (bytes[2 * i + 1].toInt() and 0xff) }
            return groups.joinToString(":") { "%x".format(it) } + "::/64"
        }

        internal val FAILURE_WINDOW: Duration = Duration.ofMinutes(1)
        internal val BLOCK_DURATION: Duration = Duration.ofMinutes(30)

        private const val MAX_TRACKED_IPS = 10_000
    }
}

/**
 * [McpAuthFailureGuard] でブロック中の IP からのリクエストに 429 を返す。
 *
 * @return 応答を返した場合は true（以降の処理を行わない）
 */
internal suspend fun McpAuthFailureGuard.respondIfBlocked(call: ApplicationCall): Boolean {
    val remaining = remainingBlock(call.request.origin.remoteAddress) ?: return false
    call.respondMcpBlocked(remaining)
    return true
}

/** ブロック中のリクエストに、解除までの秒数を `Retry-After` に付けて 429 を返す */
internal suspend fun ApplicationCall.respondMcpBlocked(remaining: Duration) {
    // 1 秒未満の端数は切り上げる
    response.header(HttpHeaders.RetryAfter, (remaining.toMillis() + 999) / 1000)
    respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Too many requests"))
}
