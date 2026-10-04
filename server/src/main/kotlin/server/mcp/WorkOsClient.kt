package server.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import server.util.CloseOnce
import server.util.defaultHttpClient

/** WorkOS AuthKit の Standalone Connect で WorkOS に渡すユーザー情報 */
data class WorkOsExternalUser(
    /** Firebase の uid。WorkOS 側では external_id として保存され、アクセストークンの `sub` にもこの値が入る */
    val id: String,
    val email: String,
)

/** WorkOS API への窓口。HTTP 通信を切り離し、呼び出し側のロジックをモックでテストできるようにする */
interface WorkOsClient : AutoCloseable {
    /**
     * Standalone Connect の完了 API を呼び、CrabShell でのログインが済んだことを WorkOS に伝える。
     * 同じ [WorkOsExternalUser.id] で呼ぶと、WorkOS 側のユーザー情報は上書きされる。
     *
     * @return 連携を続けるためにブラウザを遷移させる先（AuthKit の同意画面）
     * @throws WorkOsApiException WorkOS が成功以外を返した場合
     */
    suspend fun completeExternalAuth(
        externalAuthId: String,
        user: WorkOsExternalUser,
    ): String
}

/** WorkOS API が成功以外のステータスを返したことを表す */
class WorkOsApiException(
    message: String,
) : RuntimeException(message)

/** Ktor Client による [WorkOsClient] の実装 */
class WorkOsHttpClient(
    private val config: McpConfig,
    private val client: HttpClient = defaultHttpClient { install(ContentNegotiation) { json(workOsJson) } },
    private val baseUrl: String = WORKOS_API_BASE_URL,
) : WorkOsClient {
    private val closeOnce = CloseOnce()

    override suspend fun completeExternalAuth(
        externalAuthId: String,
        user: WorkOsExternalUser,
    ): String {
        val response =
            client.post("$baseUrl/authkit/oauth2/complete") {
                bearerAuth(config.apiKey)
                contentType(ContentType.Application.Json)
                setBody(CompleteRequest(externalAuthId, CompleteRequest.User(user.id, user.email)))
            }
        response.throwIfFailed("complete external auth")
        return response.body<CompleteResponse>().redirectUri
    }

    override fun close() = closeOnce { client.close() }

    private suspend fun HttpResponse.throwIfFailed(operation: String) {
        if (!status.isSuccess()) {
            throw WorkOsApiException("WorkOS $operation failed: ${status.value} ${bodyAsText().take(ERROR_BODY_MAX_LENGTH)}")
        }
    }

    @Serializable
    private data class CompleteRequest(
        @SerialName("external_auth_id") val externalAuthId: String,
        val user: User,
    ) {
        @Serializable
        data class User(
            val id: String,
            val email: String,
        )
    }

    @Serializable
    private data class CompleteResponse(
        @SerialName("redirect_uri") val redirectUri: String,
    )

    companion object {
        private const val WORKOS_API_BASE_URL = "https://api.workos.com"

        // ログに出すエラー本文の上限
        private const val ERROR_BODY_MAX_LENGTH = 500

        private val workOsJson = Json { ignoreUnknownKeys = true }
    }
}
