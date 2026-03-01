package server.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimitName

/** アプリケーション全体で使用するレートリミット名の定数 */
object RateLimitNames {
    /** パスキー認証（未認証エンドポイント） */
    val PASSKEY_AUTH = RateLimitName("passkey-auth")

    /** AI テキスト生成 */
    val AI_GENERATE = RateLimitName("ai-generate")
}
