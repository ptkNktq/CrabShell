package core.network

import config.HttpTimeouts
import core.auth.AuthRepository
import core.auth.IdTokenResult
import core.common.AppLogger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "HttpClient"

/** エラーレスポンスの JSON から "error" フィールドを抽出する */
private suspend fun HttpResponse.extractErrorMessage(): String {
    val body = bodyAsText()
    return try {
        Json
            .parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.content
            ?: "リクエストに失敗しました ($status)"
    } catch (_: Exception) {
        body.ifBlank { "リクエストに失敗しました ($status)" }
    }
}

fun createUnauthenticatedClient(): HttpClient =
    HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeouts.DEFAULT_REQUEST_TIMEOUT_MILLIS
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    throw Exception(response.extractErrorMessage())
                }
            }
        }
    }

/** セッションが無効になっており、再ログインが必要なことを示す。送出前に Firebase からサインアウト済み。 */
class AuthSessionExpiredException : Exception("認証エラー: 再ログインしてください")

/**
 * ネットワーク断などで ID トークンを取得・更新できなかったことを示す。
 * セッション自体は有効な可能性があるため、サインアウトはしない。
 */
class IdTokenUnavailableException(
    val code: String,
) : Exception("通信に失敗しました。ネットワーク接続を確認して再度お試しください")

fun createAuthenticatedClient(authRepository: AuthRepository): HttpClient =
    HttpClient {
        configureAuthenticatedClient(authRepository)
    }

/** 認証付き HTTP クライアントの設定。テストで MockEngine と組み合わせられるよう切り出している。 */
internal fun HttpClientConfig<*>.configureAuthenticatedClient(authRepository: AuthRepository) {
    install(HttpTimeout) {
        requestTimeoutMillis = HttpTimeouts.DEFAULT_REQUEST_TIMEOUT_MILLIS
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(ResponseObserver) {
        onResponse { response ->
            val request = response.request
            AppLogger.d(TAG, "${request.method.value} ${request.url} → ${response.status}")
        }
    }
    install(FirebaseIdTokenAuth) {
        this.authRepository = authRepository
    }
    // FirebaseIdTokenAuth で強制更新したトークンでも 401 の場合のみ、ここで 401 に到達する。
    // レスポンス検証は FirebaseIdTokenAuth の on(Send) による再送が終わった後の最終レスポンスに対して行われるため、
    // 1 回目の 401 ではサインアウトしない（AuthHttpClientTest の再送テストでこの順序を担保している）
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) {
                // 最新のトークンでも拒否された＝セッションが無効。Firebase からサインアウトして認証状態をリセットする
                AppLogger.e(TAG, "Unauthorized with refreshed ID token — signing out")
                authRepository.signOut()
                throw AuthSessionExpiredException()
            }
            if (!response.status.isSuccess()) {
                val message = response.extractErrorMessage()
                AppLogger.e(TAG, "${response.request.method.value} ${response.request.url} failed: $message")
                throw Exception(message)
            }
        }
    }
}

private class FirebaseIdTokenAuthConfig {
    lateinit var authRepository: AuthRepository
}

/**
 * リクエストごとに Firebase の ID トークンを取得して Authorization ヘッダに付ける。
 *
 * トークンをキャッシュせず毎回 [AuthRepository.getIdToken] を呼ぶことで、期限切れ・期限間近のトークンは
 * 送信前に Firebase が更新する（有効なトークンはメモリキャッシュから返るため通信は発生しない）。
 * サーバーが 401 を返した場合は、トークンを強制更新して 1 回だけ再送する。
 *
 * トークン取得に失敗した場合、セッション無効ならサインアウトし、ネットワーク断などの一時的な失敗なら
 * サインアウトせずにエラーにする（通信断だけで強制ログアウトさせないため）。
 *
 * 再送時はリクエストをそのまま再利用するため、JSON など再送可能なボディを前提とする。
 * 一度しか読めないボディ（ストリーミングアップロード等）を認証付きクライアントで送る場合は、再送方法の見直しが必要。
 * また、同時に複数のリクエストが 401 を受けた場合はそれぞれが強制更新を行う（トークンは送信前に更新されるため稀）。
 *
 * @see <a href="https://ktor.io/docs/client-custom-plugins.html">Ktor client custom plugins（on(Send) フック）</a>
 */
private val FirebaseIdTokenAuth =
    createClientPlugin("FirebaseIdTokenAuth", ::FirebaseIdTokenAuthConfig) {
        val authRepository = pluginConfig.authRepository

        suspend fun idTokenOrThrow(forceRefresh: Boolean): String? =
            when (val result = authRepository.getIdToken(forceRefresh)) {
                is IdTokenResult.Success -> result.token
                IdTokenResult.SignedOut -> null
                is IdTokenResult.SessionInvalid -> {
                    AppLogger.e(TAG, "ID token session invalid (${result.code}) — signing out")
                    authRepository.signOut()
                    throw AuthSessionExpiredException()
                }
                is IdTokenResult.TransientFailure -> throw IdTokenUnavailableException(result.code)
            }

        fun HttpRequestBuilder.setBearer(token: String) {
            headers[HttpHeaders.Authorization] = "Bearer $token"
        }

        on(Send) { request ->
            val token = idTokenOrThrow(forceRefresh = false)
            if (token == null) {
                proceed(request)
            } else {
                request.setBearer(token)
                val call = proceed(request)
                if (call.response.status != HttpStatusCode.Unauthorized) {
                    call
                } else {
                    AppLogger.w(TAG, "401 Unauthorized — retrying with force-refreshed ID token")
                    val refreshed = idTokenOrThrow(forceRefresh = true)
                    if (refreshed == null) {
                        call
                    } else {
                        request.setBearer(refreshed)
                        proceed(request)
                    }
                }
            }
        }
    }
