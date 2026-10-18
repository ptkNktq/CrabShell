package server.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/**
 * MCP の [Server] を利用者ごとに作る。
 *
 * MCP エンドポイントは stateless（リクエストごとに Server を作って捨てる）にしており、
 * ツールの処理に uid を閉じ込めることで、ツール側が認証情報を取り回さずに済むようにしている。
 */
class McpServerFactory(
    private val feedingMcpTools: FeedingMcpTools,
) {
    fun create(uid: String): Server =
        Server(
            serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION, title = "CrabShell"),
            options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        ) {
            feedingMcpTools.register(this, uid)
        }

    companion object {
        private const val SERVER_NAME = "crabshell"
        private const val SERVER_VERSION = "1.0.0"
    }
}
