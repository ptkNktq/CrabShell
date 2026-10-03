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
