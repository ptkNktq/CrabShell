package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.McpAuthorizationCompleteRequest
import model.McpAuthorizationCompleteResponse

interface McpAuthorizationRepository {
    /**
     * MCP 連携を完了する（ログイン中のユーザーを WorkOS に伝える）。
     *
     * @param externalAuthId WorkOS AuthKit が Login URI に付けて渡してきた連携手続きの ID
     * @return 連携を続けるためにブラウザを遷移させる先
     */
    suspend fun complete(externalAuthId: String): String
}

class McpAuthorizationRepositoryImpl(
    private val client: HttpClient,
) : McpAuthorizationRepository {
    override suspend fun complete(externalAuthId: String): String =
        client
            .post("/api/mcp/authorization/complete") {
                contentType(ContentType.Application.Json)
                setBody(McpAuthorizationCompleteRequest(externalAuthId = externalAuthId))
            }.body<McpAuthorizationCompleteResponse>()
            .redirectUri
}
