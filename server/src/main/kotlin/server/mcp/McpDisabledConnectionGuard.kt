package server.mcp

import org.slf4j.LoggerFactory
import server.util.FailureBlocker
import java.time.Duration
import java.time.Instant

/**
 * ユーザーが無効にした連携アプリからのリクエストをユーザー（uid）ごとに数え、短時間に続いたユーザーの `/mcp` を一定時間止める。
 *
 * 無効にしたアプリへの拒否は、同じ IP の他のアプリを巻き込まないよう [McpAuthFailureGuard] には数えない。
 * ただし、拒否されても送り続けるのは有効なトークンを持ったまま止まらないクライアント（乗っ取られた・悪意のあるアプリ）なので、
 * そのユーザーの操作ごと止める（他のアプリからのリクエストも受け付けない）。
 *
 * - 正規のクライアントは拒否されると数回の再送で止まり、ユーザーに再認証を求める（Claude Code は 1 回の操作で 6〜8 回）。
 *   そうしたクライアントを止めないよう、[REJECTION_WINDOW] の間に [MAX_REJECTIONS] 回を超えた場合だけ [BLOCK_DURATION] の間止める
 * - ブロック中は Firebase・Firestore に問い合わせずに 429 を返す
 * - 状態はメモリだけに持つ。サーバーを再起動するとブロックは解除される
 *
 * 記録とブロック状態をメモリに保持するため、アプリ全体で 1 インスタンスを共有すること（Koin の single で登録）。
 */
class McpDisabledConnectionGuard(
    now: () -> Instant = Instant::now,
    maxTrackedUsers: Int = MAX_TRACKED_USERS,
) {
    private val logger = LoggerFactory.getLogger(McpDisabledConnectionGuard::class.java)

    private val blocker =
        FailureBlocker(
            maxFailures = MAX_REJECTIONS,
            window = REJECTION_WINDOW,
            blockDuration = BLOCK_DURATION,
            maxTrackedKeys = maxTrackedUsers,
            now = now,
        )

    /** @return [uid] がブロック中なら解除までの残り時間。ブロックされていなければ null */
    fun remainingBlock(uid: String): Duration? = blocker.remainingBlock(uid)

    /** [uid] の、無効にした連携アプリからのリクエストを拒否したことを記録する。続いた場合はブロックを始める */
    fun recordRejection(uid: String) {
        if (blocker.recordFailure(uid)) {
            logger.warn("MCP requests for user {} blocked for {} after repeated requests from disabled connections", uid, BLOCK_DURATION)
        }
    }

    companion object {
        internal const val MAX_REJECTIONS = 30
        internal val REJECTION_WINDOW: Duration = Duration.ofMinutes(1)
        internal val BLOCK_DURATION: Duration = Duration.ofMinutes(30)

        private const val MAX_TRACKED_USERS = 10_000
    }
}
