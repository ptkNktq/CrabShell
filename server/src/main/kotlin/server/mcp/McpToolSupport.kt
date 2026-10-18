package server.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.Logger

/** ツールの想定外の失敗時に返す固定メッセージ */
internal const val TOOL_FAILED_MESSAGE = "処理に失敗しました。時間をおいて再度お試しください"

/** ツールの引数が正しくないことを表す。メッセージは MCP クライアントにそのまま返す */
internal class InvalidToolArgumentException(
    message: String,
) : RuntimeException(message)

/**
 * ツールを実行し、[logger] に監査ログを残す。
 * 引数の誤りは呼び出し側（AI）が直せるようにメッセージを返し、想定外の例外は内部情報を出さずに固定メッセージを返す。
 */
internal suspend fun runMcpTool(
    logger: Logger,
    toolName: String,
    uid: String,
    block: suspend () -> CallToolResult,
): CallToolResult =
    try {
        block().also { logger.info("MCP tool called: tool={} uid={}", toolName, uid) }
    } catch (e: InvalidToolArgumentException) {
        logger.info("MCP tool rejected: tool={} uid={} reason={}", toolName, uid, e.message)
        toolErrorResult(e.message ?: "引数が正しくありません")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // SDK に任せると例外メッセージ（Firestore のパス等）がそのままクライアントに返るため、ここで握る
        logger.error("MCP tool failed: tool={} uid={}", toolName, uid, e)
        toolErrorResult(TOOL_FAILED_MESSAGE)
    }

internal fun toolSuccessResult(text: String): CallToolResult = CallToolResult(content = listOf(TextContent(text)))

internal fun toolErrorResult(message: String): CallToolResult = CallToolResult(content = listOf(TextContent(message)), isError = true)

/** ツール引数 [key] の文字列値。未指定や文字列以外は null */
internal fun JsonObject?.stringArgument(key: String): String? = (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
