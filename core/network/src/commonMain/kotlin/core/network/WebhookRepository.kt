package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.WebhookSettings

interface WebhookRepository {
    suspend fun getSettings(): WebhookSettings

    suspend fun updateSettings(settings: WebhookSettings): WebhookSettings
}

class WebhookRepositoryImpl(
    private val client: HttpClient,
) : WebhookRepository {
    override suspend fun getSettings(): WebhookSettings = client.get("/api/settings/webhook").body()

    override suspend fun updateSettings(settings: WebhookSettings): WebhookSettings =
        client
            .put("/api/settings/webhook") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
}
