package server.mcp

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import java.net.URI
import java.util.concurrent.TimeUnit

private const val MCP_AUTH_PROVIDER_NAME = "mcp"

// 時計のずれとして許容する秒数（exp / nbf / iat の検証）
private const val TOKEN_LEEWAY_SECONDS = 30L

/** MCP のアクセストークンで認証された利用者 */
data class McpPrincipal(
    val uid: String,
    /** アクセストークンの `sub`（WorkOS のユーザー ID） */
    val workOsUserId: String,
    /** アクセストークンの `client_id`（MCP クライアントの自己申告。ログ用で、権限の判断には使わない） */
    val clientId: String?,
)

/** MCP 認証のルート内で [McpPrincipal] を取得する拡張プロパティ */
val ApplicationCall.mcpPrincipal: McpPrincipal
    get() = principal<McpPrincipal>()!!

/** MCP 認証必須ルートビルダー */
fun Route.mcpAuthenticated(build: Route.() -> Unit): Route = authenticate(MCP_AUTH_PROVIDER_NAME) { build() }

/**
 * 署名・`iss`・`aud` の検証を通ったアクセストークンから、CrabShell の利用者を特定する。
 *
 * - `sub`（WorkOS のユーザー ID）を uid に変換する
 * - Firebase 上で削除・無効化されたユーザーは、トークンが有効期限内でも拒否する
 */
class McpTokenAuthenticator(
    private val userResolver: McpUserResolver,
    private val firebaseAuthRepository: FirebaseAuthRepository,
) {
    private val logger = LoggerFactory.getLogger(McpTokenAuthenticator::class.java)

    /**
     * @return 利用者。特定できない、または利用できないユーザーの場合は null
     * @throws WorkOsApiException WorkOS への問い合わせに失敗した場合（一時的な失敗を認証失敗として扱わない）
     */
    suspend fun authenticate(
        workOsUserId: String,
        clientId: String?,
    ): McpPrincipal? {
        val uid = userResolver.resolveUid(workOsUserId)
        if (uid == null) {
            logger.warn("MCP token rejected: no external_id for WorkOS user {}", workOsUserId)
            return null
        }
        // Firebase Admin SDK の呼び出しはブロッキングのため IO スレッドで行う
        val status = withContext(Dispatchers.IO) { firebaseAuthRepository.getUserStatus(uid) }
        if (status != FirebaseUserStatus.ACTIVE) {
            logger.warn("MCP token rejected: user {} is {}", uid, status)
            return null
        }
        return McpPrincipal(uid = uid, workOsUserId = workOsUserId, clientId = clientId)
    }
}

/** 署名検証用の公開鍵を AuthKit の JWKS から取得する [JwkProvider] を作る（鍵はキャッシュし、取得頻度も制限する） */
fun createJwkProvider(config: McpConfig): JwkProvider =
    JwkProviderBuilder(URI(config.jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

/**
 * MCP のアクセストークン（WorkOS AuthKit が発行した JWT）を検証する認証プロバイダーを登録する。
 *
 * 認証に失敗した場合は、MCP クライアントが認可サーバーを見つけられるよう
 * `WWW-Authenticate` に Protected Resource Metadata の URL を付けて 401 を返す。
 */
fun AuthenticationConfig.mcpJwt(
    config: McpConfig,
    jwkProvider: JwkProvider,
    authenticator: McpTokenAuthenticator,
) {
    jwt(MCP_AUTH_PROVIDER_NAME) {
        realm = "CrabShell MCP"
        verifier(jwkProvider, config.issuer) {
            withAudience(config.resource)
            acceptLeeway(TOKEN_LEEWAY_SECONDS)
        }
        validate { credential ->
            val subject = credential.payload.subject ?: return@validate null
            authenticator.authenticate(subject, credential.payload.getClaim("client_id").asString())
        }
        challenge { _, _ ->
            val error =
                when (call.authentication.allFailures.firstOrNull()) {
                    null, AuthenticationFailedCause.NoCredentials -> ""
                    else -> "error=\"invalid_token\", "
                }
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer ${error}resource_metadata=\"${config.resourceMetadataUrl}\"")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        }
    }
}
