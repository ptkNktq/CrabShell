package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.PaymentWebhookSettings

interface PaymentWebhookRepository {
    suspend fun getSettings(): PaymentWebhookSettings

    suspend fun updateSettings(settings: PaymentWebhookSettings): PaymentWebhookSettings
}

class PaymentWebhookRepositoryImpl(
    private val client: HttpClient,
) : PaymentWebhookRepository {
    override suspend fun getSettings(): PaymentWebhookSettings = client.get("/api/settings/payment-webhook").body()

    override suspend fun updateSettings(settings: PaymentWebhookSettings): PaymentWebhookSettings =
        client
            .put("/api/settings/payment-webhook") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
}
