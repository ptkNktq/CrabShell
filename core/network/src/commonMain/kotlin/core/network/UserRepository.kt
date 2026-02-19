package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.UpdateDisplayNameRequest
import model.User

interface UserRepository {
    suspend fun getUsers(): List<User>

    suspend fun updateDisplayName(
        uid: String,
        displayName: String,
    ): User
}

class UserRepositoryImpl(
    private val client: HttpClient,
) : UserRepository {
    override suspend fun getUsers(): List<User> = client.get("/api/users").body()

    override suspend fun updateDisplayName(
        uid: String,
        displayName: String,
    ): User =
        client
            .put("/api/users/$uid/name") {
                contentType(ContentType.Application.Json)
                setBody(UpdateDisplayNameRequest(displayName))
            }.body()
}
