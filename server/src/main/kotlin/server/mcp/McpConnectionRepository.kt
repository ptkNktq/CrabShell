package server.mcp

import java.time.Instant

/**
 * 連携アプリ（ユーザー × MCP クライアント）の記録。
 *
 * @property id [mcpConnectionId] で client_id から作る ID
 * @property clientId MCP クライアントの client_id（CIMD の URL）
 * @property clientName クライアントのメタデータの `client_name`。未取得・取得できなかった場合は null
 * @property clientNameFetchedAt [clientName] を最後に取得しようとした日時（失敗した場合も更新する）
 * @property disabledAt ユーザーが無効にした日時。有効なら null
 */
data class McpConnectionRecord(
    val id: String,
    val clientId: String,
    val clientName: String?,
    val clientNameFetchedAt: Instant?,
    val firstUsedAt: Instant,
    val lastUsedAt: Instant,
    val disabledAt: Instant?,
)

interface McpConnectionRepository {
    suspend fun find(
        uid: String,
        connectionId: String,
    ): McpConnectionRecord?

    /** 最終利用日時の新しい順にすべて（記録は本人が連携を許可したアプリの分だけで、多くならない） */
    suspend fun list(uid: String): List<McpConnectionRecord>

    /** 無効にした連携アプリだけを、無効にした日の新しい順に */
    suspend fun listDisabled(uid: String): List<McpConnectionRecord>

    /** 新しく記録する。同じ ID の記録が既にあれば何もしない（同時に来た初回のリクエストで上書きしないため） */
    suspend fun createIfAbsent(
        uid: String,
        record: McpConnectionRecord,
    )

    suspend fun updateLastUsedAt(
        uid: String,
        connectionId: String,
        lastUsedAt: Instant,
    )

    suspend fun updateClientName(
        uid: String,
        connectionId: String,
        clientName: String?,
        fetchedAt: Instant,
    )

    /**
     * 無効にした日時を設定する（null で有効に戻す）。
     * @return 更新後の記録。対象がなければ null
     */
    suspend fun updateDisabledAt(
        uid: String,
        connectionId: String,
        disabledAt: Instant?,
    ): McpConnectionRecord?
}
