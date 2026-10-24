package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import model.McpConnection
import model.McpConnectionsResponse

/** MCP の連携アプリ（Claude Code 等）の一覧と、無効化・有効化 */
interface McpConnectionRepository {
    suspend fun getConnections(): McpConnectionsResponse

    /** 無効にした連携アプリだけ（無効にした日の新しい順。表示名は取り直さない） */
    suspend fun getDisabledConnections(): McpConnectionsResponse

    /** @return 更新後の連携アプリ */
    suspend fun disable(id: String): McpConnection

    /** @return 更新後の連携アプリ */
    suspend fun enable(id: String): McpConnection
}

class McpConnectionRepositoryImpl(
    private val client: HttpClient,
) : McpConnectionRepository {
    override suspend fun getConnections(): McpConnectionsResponse = client.get("/api/mcp/connections").body()

    override suspend fun getDisabledConnections(): McpConnectionsResponse = client.get("/api/mcp/connections/disabled").body()

    override suspend fun disable(id: String): McpConnection = client.post("/api/mcp/connections/$id/disable").body()

    override suspend fun enable(id: String): McpConnection = client.post("/api/mcp/connections/$id/enable").body()
}
