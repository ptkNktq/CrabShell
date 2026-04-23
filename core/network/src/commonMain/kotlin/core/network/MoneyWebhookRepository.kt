package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.MoneyWebhookSettings

interface MoneyWebhookRepository {
    suspend fun getSettings(): MoneyWebhookSettings

    suspend fun updateSettings(settings: MoneyWebhookSettings): MoneyWebhookSettings
}

class MoneyWebhookRepositoryImpl(
    private val client: HttpClient,
) : MoneyWebhookRepository {
    override suspend fun getSettings(): MoneyWebhookSettings = client.get("/api/settings/money-webhook").body()

    override suspend fun updateSettings(settings: MoneyWebhookSettings): MoneyWebhookSettings =
        client
            .put("/api/settings/money-webhook") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
}
