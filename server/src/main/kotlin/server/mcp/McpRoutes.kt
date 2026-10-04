package server.mcp

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import model.McpAuthorizationCompleteRequest
import model.McpAuthorizationCompleteResponse
import model.McpAuthorizationErrors
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.ratelimit.RateLimitNames

// MCP リクエストの本文の上限。既存 API（RequestBodyLimit）と揃える
private const val MCP_MAX_REQUEST_BODY_SIZE = 256_000L

/** Protected Resource Metadata（RFC 9728）。MCP クライアントはここから認可サーバー（AuthKit）を知る */
@Serializable
private data class ProtectedResourceMetadata(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>,
    @SerialName("bearer_methods_supported") val bearerMethodsSupported: List<String>,
)

/**
 * MCP 連携の完了 API（`/api` 配下）。
 *
 * WorkOS AuthKit の Login URI（フロントエンドの `/mcp-connect`）でログインしたユーザーが、
 * 連携を続ける操作をしたときに呼ばれる。
 */
fun Route.mcpAuthorizationRoutes() {
    val mcpAuthorizationService by inject<McpAuthorizationService>()

    authenticated {
        rateLimit(RateLimitNames.MCP_AUTHORIZATION) {
            post("/mcp/authorization/complete", {
                tags = listOf("mcp")
                summary = "MCP 連携の完了"
                request {
                    body<McpAuthorizationCompleteRequest>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<McpAuthorizationCompleteResponse>()
                    }
                    code(HttpStatusCode.BadRequest) { description = "external_auth_id が不正、またはメールアドレス未登録" }
                    code(HttpStatusCode.BadGateway) { description = "WorkOS API の呼び出しに失敗、または想定外の応答" }
                }
            }) {
                val principal = call.firebasePrincipal
                val externalAuthId = call.receive<McpAuthorizationCompleteRequest>().externalAuthId
                when (val result = mcpAuthorizationService.complete(principal.uid, principal.email, externalAuthId)) {
                    is McpAuthorizationResult.Completed ->
                        call.respond(McpAuthorizationCompleteResponse(redirectUri = result.redirectUri))
                    McpAuthorizationResult.InvalidExternalAuthId ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to McpAuthorizationErrors.INVALID_EXTERNAL_AUTH_ID))
                    McpAuthorizationResult.MissingEmail ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to McpAuthorizationErrors.EMAIL_NOT_REGISTERED))
                    McpAuthorizationResult.UpstreamFailure ->
                        call.respond(HttpStatusCode.BadGateway, mapOf("error" to McpAuthorizationErrors.UPSTREAM_FAILURE))
                }
            }
        }
    }
}

/**
 * MCP エンドポイント（`/mcp`）と、その Protected Resource Metadata。
 *
 * - stateless な Streamable HTTP（POST のみ）。リクエストごとに利用者の uid を閉じ込めた MCP Server を作って捨てる。
 *   SDK の `mcpStatelessStreamableHttp` はアプリ直下にルートを作るため認証で囲めず、同じ処理を認証の内側に書いている
 * - JSON-RPC の応答は MCP 仕様の JSON 設定（[McpJson]）で返す必要があるため、このルートだけ ContentNegotiation を差し替える
 * - DNS リバインディング対策（SDK の DnsRebindingProtection）は入れない。ブラウザが勝手に付けられない Bearer トークンで
 *   認証しており、攻撃が成立しないため。リバースプロキシ配下で Host が書き換わる環境で誤検知させないためでもある
 */
fun Route.mcpRoutes(config: McpConfig) {
    val mcpServerFactory by inject<McpServerFactory>()
    val authFailureGuard by inject<McpAuthFailureGuard>()

    val metadata =
        ProtectedResourceMetadata(
            resource = config.resource,
            authorizationServers = listOf(config.issuer),
            bearerMethodsSupported = listOf("header"),
        )
    get(MCP_RESOURCE_METADATA_PATH) { call.respond(metadata) }

    route(MCP_PATH) {
        install(ContentNegotiation) { json(McpJson) }
        // 認証に失敗し続けた IP は、トークンの検証より前に弾く
        install(McpAuthFailureGuardPlugin) { guard = authFailureGuard }

        mcpAuthenticated {
            rateLimit(RateLimitNames.MCP) {
                post {
                    val transport =
                        StreamableHttpServerTransport(
                            StreamableHttpServerTransport.Configuration(
                                enableJsonResponse = true,
                                maxRequestBodySize = MCP_MAX_REQUEST_BODY_SIZE,
                            ),
                        ).also { it.setSessionIdGenerator(null) }
                    val session = mcpServerFactory.create(call.mcpPrincipal.uid).createSession(transport)
                    try {
                        transport.handleRequest(null, call)
                    } finally {
                        // 1 リクエストで使い捨てるため、セッションを閉じて登録を残さない
                        session.close()
                    }
                }
            }
        }

        // stateless のため、SSE ストリーム（GET）とセッション終了（DELETE）は提供しない
        get { call.respondMethodNotAllowed() }
        delete { call.respondMethodNotAllowed() }
    }
}

private suspend fun ApplicationCall.respondMethodNotAllowed() {
    response.header(HttpHeaders.Allow, HttpMethod.Post.value)
    respond(HttpStatusCode.MethodNotAllowed, mapOf("error" to "Method not allowed"))
}
