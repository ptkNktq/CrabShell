package frontend.auth

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*

val authenticatedClient = HttpClient {
    install(ContentNegotiation) { json() }
    defaultRequest {
        val token = AuthStateHolder.idToken
        if (token != null) {
            headers.append("Authorization", "Bearer $token")
        }
    }
}
