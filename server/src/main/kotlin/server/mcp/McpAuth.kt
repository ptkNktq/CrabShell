package server.mcp

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.NetworkException
import com.auth0.jwk.RateLimitReachedException
import com.auth0.jwt.JWT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
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
     * @return 認証の結果。Firebase への問い合わせに失敗した場合は [McpAuthResult.Unavailable]
     *  （一時的な失敗をトークン不正として扱うと、クライアントが再認可を始めてしまうため区別する）
     */
    suspend fun authenticate(
        uid: String,
        clientId: String?,
    ): McpAuthResult {
        // 無効化・削除をすぐ反映させるため、結果はキャッシュせずリクエストごとに照会する。
        // Firebase Admin SDK の呼び出しはブロッキングのため IO スレッドで行う
        val status =
            try {
                withContext(Dispatchers.IO) { firebaseAuthRepository.getUserStatus(uid) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("MCP authentication unavailable: failed to get user status for {}", uid, e)
                return McpAuthResult.Unavailable
            }
        if (status != FirebaseUserStatus.ACTIVE) {
            logger.warn("MCP token rejected: user {} is {}", uid, status)
            return McpAuthResult.Rejected
        }
        return McpAuthResult.Authenticated(McpPrincipal(uid = uid, clientId = clientId))
    }

    /**
     * 拒否したトークンのクレームをログに出す（設定の食い違いを切り分けるため）。
     * トークン本体は出さない。署名を検証していない値なので、ログ以外には使わない。
     *
     * 未認証の誰でも送れる値のため、改行で偽のログ行を作られたり長い値でログを膨らまされたりしないよう
     * [sanitizeClaimForLog] を通す。失敗が続いた IP は [McpAuthFailureGuard] がブロックするため、
     * 出力件数は IP ごとに抑えられる。
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

/** [McpTokenAuthenticator.authenticate] の結果 */
sealed interface McpAuthResult {
    data class Authenticated(
        val principal: McpPrincipal,
    ) : McpAuthResult

    /** 利用できないユーザー（削除・無効化） */
    data object Rejected : McpAuthResult

    /** 一時的な障害で判定できなかった（トークンの不正ではない） */
    data object Unavailable : McpAuthResult
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

/** 一時的な障害で認証を判定できなかったことを、validate から challenge へ伝える */
private val McpAuthUnavailableKey = AttributeKey<Unit>("McpAuthUnavailable")

// 一時的な障害のときにクライアントへ伝える再試行までの秒数（JWKS の取得頻度の上限が 1 分単位のため）
private const val MCP_UNAVAILABLE_RETRY_AFTER_SECONDS = 60

/** 一時的な障害で認証を判定できないことを 503 で返す。トークンの不正ではないため、401 にして再認可させない */
internal suspend fun ApplicationCall.respondMcpUnavailable() {
    response.header(HttpHeaders.RetryAfter, MCP_UNAVAILABLE_RETRY_AFTER_SECONDS)
    respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Service unavailable"))
}

/** リクエストの Bearer トークン。付いていなければ null */
private fun ApplicationCall.bearerToken(): String? =
    (request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
        ?.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }
        ?.blob

/**
 * MCP の認証失敗として 401 を返す。失敗を [authFailureGuard] に記録し、拒否したトークンのクレームをログに出す。
 * MCP クライアントが認可サーバーを見つけられるよう、`WWW-Authenticate` に Protected Resource Metadata の URL を付ける。
 *
 * @param invalidToken トークンが付いていて不正だった場合は true（`error="invalid_token"` を付ける）
 */
internal suspend fun ApplicationCall.respondMcpUnauthorized(
    config: McpConfig,
    authenticator: McpTokenAuthenticator,
    authFailureGuard: McpAuthFailureGuard,
    invalidToken: Boolean,
) {
    authFailureGuard.recordFailure(request.origin.remoteAddress)
    bearerToken()?.let { authenticator.logRejectedToken(it) }
    val error = if (invalidToken) "error=\"invalid_token\", " else ""
    response.header(HttpHeaders.WWWAuthenticate, "Bearer ${error}resource_metadata=\"${config.resourceMetadataUrl}\"")
    respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
}

/**
 * トークンの署名検証に使う公開鍵を、認証より前に取得できるか確かめる。
 *
 * Ktor の jwt 認証は JWKS の取得失敗を素の 401 にし、challenge も経由しない。そのままだと AuthKit の JWKS に
 * 一時的に届かないだけで、正しいトークンを持つクライアントに再認可させてしまうため、ここで判定する。
 *
 * - 通信の失敗: 503（失敗に数えない）
 * - 取得頻度の上限: 503。未知の kid の連打で上限を使い切らせ続けられないよう、失敗にも数える
 *   （正規のクライアントは `Retry-After` に従うため、ほとんど数えられない）
 * - 該当する鍵がない: ここで 401 にして失敗に数える（認証に回すと JWKS をもう一度取りに行くため）
 *
 * 取得できた鍵はキャッシュされ、続く認証で再利用される。JWT でない場合は何もせず、認証にトークン不正として扱わせる。
 */
class McpJwksPreflight(
    private val config: McpConfig,
    private val jwkProvider: JwkProvider,
    private val authenticator: McpTokenAuthenticator,
    private val authFailureGuard: McpAuthFailureGuard,
) {
    private val logger = LoggerFactory.getLogger(McpJwksPreflight::class.java)

    /** 必要なら応答を返す。返した場合、以降の処理（認証）は行われない */
    suspend fun check(call: ApplicationCall) {
        val token = call.bearerToken() ?: return
        val decoded = runCatching { JWT.decode(token) }.getOrNull() ?: return
        try {
            // kid がなくても認証は jwkProvider.get(null) を呼ぶため、同じく確かめる
            withContext(Dispatchers.IO) { jwkProvider.get(decoded.keyId) }
        } catch (e: NetworkException) {
            // 実際に JWKS を取りに行って失敗した場合だけ（取得頻度の上限で回数は抑えられる）
            logger.warn("MCP JWKS unavailable", e)
            call.respondMcpUnavailable()
        } catch (_: RateLimitReachedException) {
            // 未知の kid を連打されても出力が増えないよう、ログは出さない
            authFailureGuard.recordFailure(call.request.origin.remoteAddress)
            call.respondMcpUnavailable()
        } catch (_: JwkException) {
            call.respondMcpUnauthorized(config, authenticator, authFailureGuard, invalidToken = true)
        }
    }
}

class McpAuthPreflightPluginConfig {
    lateinit var authFailureGuard: McpAuthFailureGuard
    lateinit var jwksPreflight: McpJwksPreflight
}

/**
 * MCP の認証より前に行う確認をまとめたプラグイン。
 * ブロック中の IP を先に弾き、そのあとで JWKS を確かめる（同じルートの別々のプラグインにすると実行順が保証されないため 1 つにする）。
 */
val McpAuthPreflightPlugin =
    createRouteScopedPlugin("McpAuthPreflight", ::McpAuthPreflightPluginConfig) {
        val authFailureGuard = pluginConfig.authFailureGuard
        val jwksPreflight = pluginConfig.jwksPreflight
        onCall { call ->
            if (authFailureGuard.respondIfBlocked(call)) return@onCall
            jwksPreflight.check(call)
        }
    }

/**
 * MCP のアクセストークン（WorkOS AuthKit が発行した JWT）を検証する認証プロバイダーを登録する。
 *
 * 認証に失敗した場合は [respondMcpUnauthorized] で 401 を返し、失敗が続いた IP を [authFailureGuard] にブロックさせる。
 * Firebase への問い合わせなど一時的な障害で判定できない場合は、失敗に数えずに 503 を返す。
 */
fun AuthenticationConfig.mcpJwt(
    config: McpConfig,
    jwkProvider: JwkProvider,
    authenticator: McpTokenAuthenticator,
    authFailureGuard: McpAuthFailureGuard,
) {
    jwt(MCP_AUTH_PROVIDER_NAME) {
        realm = "CrabShell MCP"
        verifier(jwkProvider, config.issuer) {
            withAudience(config.resource)
            acceptLeeway(TOKEN_LEEWAY_SECONDS)
        }
        validate { credential ->
            val subject = credential.payload.subject ?: return@validate null
            when (val result = authenticator.authenticate(subject, credential.payload.getClaim("client_id").asString())) {
                is McpAuthResult.Authenticated -> result.principal
                McpAuthResult.Rejected -> null
                McpAuthResult.Unavailable -> {
                    // 例外を投げると Ktor が素の 401 にしてしまうため、印を付けて challenge で 503 にする
                    attributes.put(McpAuthUnavailableKey, Unit)
                    null
                }
            }
        }
        challenge { _, _ ->
            if (McpAuthUnavailableKey in call.attributes) {
                // 一時的な障害は失敗に数えない
                call.respondMcpUnavailable()
                return@challenge
            }
            val invalidToken =
                when (call.authentication.allFailures.firstOrNull()) {
                    null, AuthenticationFailedCause.NoCredentials -> false
                    else -> true
                }
            call.respondMcpUnauthorized(config, authenticator, authFailureGuard, invalidToken)
        }
    }
}
