package server.mcp

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
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
    /** アクセストークンの `sub`。Standalone Connect の完了 API に渡した Firebase の uid がそのまま入る */
    val uid: String,
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
 * - `sub` は完了 API に渡した Firebase の uid なので、そのまま uid として使う
 * - Firebase 上で削除・無効化されたユーザーは、トークンが有効期限内でも拒否する
 */
class McpTokenAuthenticator(
    private val firebaseAuthRepository: FirebaseAuthRepository,
) {
    private val logger = LoggerFactory.getLogger(McpTokenAuthenticator::class.java)

    /**
     * @param uid アクセストークンの `sub`
     * @return 利用者。利用できないユーザーの場合は null
     * @throws com.google.firebase.auth.FirebaseAuthException Firebase への問い合わせに失敗した場合。
     *  一時的な失敗で null（401 invalid_token）を返すとクライアントが再認可を始めてしまうため、そのまま投げて 500 にする
     */
    suspend fun authenticate(
        uid: String,
        clientId: String?,
    ): McpPrincipal? {
        // Firebase Admin SDK の呼び出しはブロッキングのため IO スレッドで行う
        val status = withContext(Dispatchers.IO) { firebaseAuthRepository.getUserStatus(uid) }
        if (status != FirebaseUserStatus.ACTIVE) {
            logger.warn("MCP token rejected: user {} is {}", uid, status)
            return null
        }
        return McpPrincipal(uid = uid, clientId = clientId)
    }

    /**
     * 拒否したトークンのクレームをログに出す（設定の食い違いを切り分けるため）。
     * トークン本体は出さない。署名を検証していない値なので、ログ以外には使わない。
     *
     * 未認証の誰でも送れる値のため、改行で偽のログ行を作られたり長い値でログを膨らまされたりしないよう
     * [sanitizeClaimForLog] を通す。MCP のレート制限は認証の内側にかけているため、出力件数は
     * 未認証リクエストの数だけ増える。
     */
    fun logRejectedToken(token: String) {
        val decoded = runCatching { JWT.decode(token) }.getOrNull()
        if (decoded == null) {
            logger.warn("MCP token rejected: not a JWT")
            return
        }
        logger.warn(
            "MCP token rejected: iss={} aud={} sub={} exp={}",
            sanitizeClaimForLog(decoded.issuer),
            sanitizeClaimForLog(decoded.audience?.joinToString(",")),
            sanitizeClaimForLog(decoded.subject),
            decoded.expiresAtAsInstant,
        )
    }
}

/** ログに出すクレームの最大文字数 */
private const val MAX_LOGGED_CLAIM_LENGTH = 128

// C0/C1 制御文字に加え、行区切り（U+2028）・段落区切り（U+2029）も改行として扱われうるため対象にする
private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]")

/** 署名を検証していないクレームを、制御文字を `?` に置き換え [MAX_LOGGED_CLAIM_LENGTH] 文字に切り詰めてログ用にする */
internal fun sanitizeClaimForLog(value: String?): String? = value?.replace(CONTROL_CHARACTERS, "?")?.take(MAX_LOGGED_CLAIM_LENGTH)

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
            (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
                ?.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }
                ?.let { authenticator.logRejectedToken(it.blob) }
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer ${error}resource_metadata=\"${config.resourceMetadataUrl}\"")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        }
    }
}
