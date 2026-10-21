package model

import kotlinx.serialization.Serializable

/**
 * CrabShell の MCP を使った連携アプリ（ユーザー × MCP クライアント）。
 *
 * WorkOS は許可をユーザー × クライアント単位でしか持たないため、同じアプリを複数の端末で使っていても 1 件になる。
 *
 * @property id 連携アプリの ID（無効化・有効化の API で使う）
 * @property clientName アプリの表示名（クライアントのメタデータの `client_name`。取得できない場合はホスト名）
 * @property firstUsedAt 初めて MCP を使った日時（ISO 8601）
 * @property lastUsedAt 最後に MCP を使った日時（ISO 8601）
 * @property disabledAt 無効にした日時（ISO 8601）。有効なら null
 */
@Serializable
data class McpConnection(
    val id: String,
    val clientName: String,
    val firstUsedAt: String,
    val lastUsedAt: String,
    val disabledAt: String? = null,
)

/**
 * `GET /api/mcp/connections` のレスポンス DTO。
 *
 * @property enabled サーバーで MCP 連携が有効か。無効の場合 [connections] は空
 */
@Serializable
data class McpConnectionsResponse(
    val enabled: Boolean,
    val connections: List<McpConnection>,
)
