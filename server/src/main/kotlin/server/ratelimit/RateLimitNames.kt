package server.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimitName

/** アプリケーション全体で使用するレートリミット名の定数 */
object RateLimitNames {
    /** パスキー認証（未認証エンドポイント） */
    val PASSKEY_AUTH = RateLimitName("passkey-auth")

    /** AI テキスト生成 */
    val AI_GENERATE = RateLimitName("ai-generate")

    /** ログイン履歴記録 */
    val LOGIN_HISTORY = RateLimitName("login-history")

    /** MCP エンドポイント（AI からの操作） */
    val MCP = RateLimitName("mcp")

    /** MCP 連携の完了（WorkOS API を呼ぶ） */
    val MCP_AUTHORIZATION = RateLimitName("mcp-authorization")
}
