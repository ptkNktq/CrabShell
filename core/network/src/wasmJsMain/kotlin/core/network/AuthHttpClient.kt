package core.network

import core.auth.AuthStateHolder
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createUnauthenticatedClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
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
            }
        }
    }
