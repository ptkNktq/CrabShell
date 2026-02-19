package core.network

import core.auth.AuthRepository
import core.auth.AuthStateHolder
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** エラーレスポンスの JSON から "error" フィールドを抽出する */
private suspend fun HttpResponse.extractErrorMessage(): String {
    val body = bodyAsText()
    return try {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            ?: "リクエストに失敗しました ($status)"
    } catch (_: Exception) {
        body.ifBlank { "リクエストに失敗しました ($status)" }
    }
}

fun createUnauthenticatedClient(): HttpClient =
    HttpClient {
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

fun createAuthenticatedClient(authRepository: AuthRepository): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            val token = AuthStateHolder.idToken
            if (token != null) {
                headers.append("Authorization", "Bearer $token")
            }
        }
        // 401 時にトークンを強制リフレッシュして1回だけリトライ
        install(HttpRequestRetry) {
            retryIf(maxRetries = 1) { _, response ->
                response.status == HttpStatusCode.Unauthorized
            }
            delay {
                // delay は suspend なのでトークンリフレッシュを実行できる
                authRepository.refreshToken()
            }
            modifyRequest { request ->
                val token = AuthStateHolder.idToken
                if (token != null) {
                    request.headers.remove("Authorization")
                    request.headers.append("Authorization", "Bearer $token")
                }
            }
        }
        // リトライ後も 401 の場合のみここに到達
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    // Firebase からサインアウトして認証状態を完全にリセット
                    authRepository.signOut()
                    throw Exception("認証エラー: 再ログインしてください")
                }
                if (!response.status.isSuccess()) {
                    throw Exception(response.extractErrorMessage())
                }
            }
        }
    }
