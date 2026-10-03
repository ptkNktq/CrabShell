package server.mcp

import org.slf4j.LoggerFactory

/** MCP 連携の完了（Standalone Connect の Login URI でログインした後の処理）の結果 */
sealed interface McpAuthorizationResult {
    /** 完了した。ブラウザを [redirectUri] に遷移させて連携を続ける */
    data class Completed(
        val redirectUri: String,
    ) : McpAuthorizationResult

    /** external_auth_id の形式が正しくない */
    data object InvalidExternalAuthId : McpAuthorizationResult

    /** ユーザーにメールアドレスが登録されていない（WorkOS の完了 API で必須） */
    data object MissingEmail : McpAuthorizationResult

    /** WorkOS API の呼び出しに失敗した */
    data object UpstreamFailure : McpAuthorizationResult
}

/** MCP 連携の完了処理。CrabShell でログイン済みのユーザーを WorkOS に伝え、トークン発行へ進める */
class McpAuthorizationService(
    private val workOsClient: WorkOsClient,
) {
    private val logger = LoggerFactory.getLogger(McpAuthorizationService::class.java)

    suspend fun complete(
        uid: String,
        email: String?,
        externalAuthId: String,
    ): McpAuthorizationResult {
        if (!EXTERNAL_AUTH_ID_PATTERN.matches(externalAuthId)) return McpAuthorizationResult.InvalidExternalAuthId
        if (email.isNullOrBlank()) return McpAuthorizationResult.MissingEmail
        return try {
            val redirectUri = workOsClient.completeExternalAuth(externalAuthId, WorkOsExternalUser(id = uid, email = email))
            logger.info("MCP authorization completed: uid={}", uid)
            McpAuthorizationResult.Completed(redirectUri)
        } catch (e: WorkOsApiException) {
            logger.warn("MCP authorization failed: uid={}", uid, e)
            McpAuthorizationResult.UpstreamFailure
        }
    }

    companion object {
        // WorkOS の ID（ULID 等）を想定した許容範囲。任意の文字列を WorkOS へそのまま送らないための形式チェック
        private val EXTERNAL_AUTH_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
    }
}
