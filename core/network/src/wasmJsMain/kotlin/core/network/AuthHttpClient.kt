package core.network

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

fun createAuthenticatedClient(): HttpClient =
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
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    AuthStateHolder.setUnauthenticated()
                    throw Exception("認証エラー: 再ログインしてください")
                }
                if (!response.status.isSuccess()) {
                    throw Exception(response.extractErrorMessage())
                }
            }
        }
    }
