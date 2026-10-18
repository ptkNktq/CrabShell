package model

import kotlinx.serialization.Serializable

/**
 * `POST /api/mcp/authorization/complete` のリクエスト DTO。
 *
 * @property externalAuthId WorkOS AuthKit が Login URI に付けて渡してくる、連携手続きの一時 ID
 */
@Serializable
data class McpAuthorizationCompleteRequest(
    val externalAuthId: String,
)

/**
 * `POST /api/mcp/authorization/complete` のレスポンス DTO。
 *
 * @property redirectUri 連携を続けるためにブラウザを遷移させる先（WorkOS AuthKit の同意画面）
 */
@Serializable
data class McpAuthorizationCompleteResponse(
    val redirectUri: String,
)

/** `POST /api/mcp/authorization/complete` が返すエラー（レスポンスの `error`）。クライアントが原因ごとに案内を出し分けられるよう共有する */
object McpAuthorizationErrors {
    const val INVALID_EXTERNAL_AUTH_ID = "Invalid externalAuthId"
    const val EMAIL_NOT_REGISTERED = "Email is not registered"
    const val UPSTREAM_FAILURE = "Failed to complete authorization"
}
