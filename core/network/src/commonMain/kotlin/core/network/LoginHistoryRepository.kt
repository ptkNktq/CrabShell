package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.LoginEvent
import model.RecordLoginRequest

interface LoginHistoryRepository {
    suspend fun recordLogin(loginMethod: String)

    suspend fun getLoginHistory(limit: Int = 50): List<LoginEvent>
}

class LoginHistoryRepositoryImpl(
    private val client: HttpClient,
) : LoginHistoryRepository {
    override suspend fun recordLogin(loginMethod: String) {
        client.post("/api/login-history") {
            contentType(ContentType.Application.Json)
            setBody(RecordLoginRequest(loginMethod))
        }
    }

    override suspend fun getLoginHistory(limit: Int): List<LoginEvent> =
        client
            .get("/api/login-history") {
                parameter("limit", limit)
            }.body()
}
