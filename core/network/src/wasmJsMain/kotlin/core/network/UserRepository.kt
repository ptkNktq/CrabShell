package core.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.UpdateDisplayNameRequest
import model.User

object UserRepository {
    suspend fun getUsers(): List<User> = authenticatedClient.get("/api/users").body()

    suspend fun updateDisplayName(
        uid: String,
        displayName: String,
    ): User =
        authenticatedClient.put("/api/users/$uid/name") {
            contentType(ContentType.Application.Json)
            setBody(UpdateDisplayNameRequest(displayName))
        }.body()
}
