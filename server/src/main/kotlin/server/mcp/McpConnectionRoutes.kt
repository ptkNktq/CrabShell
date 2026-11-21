package server.mcp

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.util.getOrFail
import model.McpConnection
import model.McpConnectionsResponse
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.ratelimit.RateLimitNames

/**
 * 連携アプリの一覧・無効化・有効化の API（`/api` 配下）。設定画面と MCP 連携画面から呼ばれる。
 *
 * MCP が無効でも登録する（一覧は `enabled = false` を返し、画面側で案内を出すため）。
 * 無効化・有効化は、MCP が無効なときは 404 にして記録を変えない。
 */
fun Route.mcpConnectionRoutes() {
    val mcpConfig by inject<McpConfig>()
    val connectionService by inject<McpConnectionService>()

    authenticated {
        // 無効にした連携アプリ（MCP 連携画面用）。Firestore を読むだけで外部へは通信しないため、レート制限の対象にしない
        // （設定画面の操作と制限を共有すると、連携画面の読み込みが 429 で失敗しうる）
        get("/mcp/connections/disabled", {
            tags = listOf("mcp")
            summary = "無効にした連携アプリの一覧（無効にした日の新しい順）"
            response {
                code(HttpStatusCode.OK) {
                    body<McpConnectionsResponse>()
                }
            }
        }) {
            val connections = if (mcpConfig.enabled) connectionService.listDisabled(call.firebasePrincipal.uid) else emptyList()
            call.respond(McpConnectionsResponse(enabled = mcpConfig.enabled, connections = connections))
        }

        // 一覧ではメタデータの取得（外部への通信）が起こりうるため、連打で外部へ通信させすぎないようにする
        rateLimit(RateLimitNames.MCP_CONNECTIONS) {
            get("/mcp/connections", {
                tags = listOf("mcp")
                summary = "連携アプリの一覧"
                response {
                    code(HttpStatusCode.OK) {
                        body<McpConnectionsResponse>()
                    }
                }
            }) {
                val connections = if (mcpConfig.enabled) connectionService.list(call.firebasePrincipal.uid) else emptyList()
                call.respond(McpConnectionsResponse(enabled = mcpConfig.enabled, connections = connections))
            }

            post("/mcp/connections/{id}/disable", {
                tags = listOf("mcp")
                summary = "連携アプリを無効にする"
                request {
                    pathParameter<String>("id") { description = "連携アプリの ID" }
                }
                response {
                    code(HttpStatusCode.OK) { body<McpConnection>() }
                    code(HttpStatusCode.BadRequest) { description = "ID が不正" }
                    code(HttpStatusCode.NotFound) { description = "対象の連携アプリが見つからない、または MCP 連携が無効" }
                }
            }) {
                respondSetDisabled(mcpConfig, connectionService, disabled = true)
            }

            post("/mcp/connections/{id}/enable", {
                tags = listOf("mcp")
                summary = "連携アプリを有効にする"
                request {
                    pathParameter<String>("id") { description = "連携アプリの ID" }
                }
                response {
                    code(HttpStatusCode.OK) { body<McpConnection>() }
                    code(HttpStatusCode.BadRequest) { description = "ID が不正" }
                    code(HttpStatusCode.NotFound) { description = "対象の連携アプリが見つからない、または MCP 連携が無効" }
                }
            }) {
                respondSetDisabled(mcpConfig, connectionService, disabled = false)
            }
        }
    }
}

private suspend fun RoutingContext.respondSetDisabled(
    mcpConfig: McpConfig,
    connectionService: McpConnectionService,
    disabled: Boolean,
) {
    // 一覧が空を返すのと合わせ、MCP が無効なときは記録を変えない
    if (!mcpConfig.enabled) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "MCP integration is disabled"))
        return
    }
    val id = call.parameters.getOrFail("id")
    if (!isMcpConnectionId(id)) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))
        return
    }
    val connection = connectionService.setDisabled(call.firebasePrincipal.uid, id, disabled)
    if (connection == null) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Connection not found"))
        return
    }
    call.respond(connection)
}
