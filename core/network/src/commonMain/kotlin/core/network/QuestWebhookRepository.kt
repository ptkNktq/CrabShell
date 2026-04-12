package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.QuestWebhookSettings

interface QuestWebhookRepository {
    suspend fun getSettings(): QuestWebhookSettings

    suspend fun updateSettings(settings: QuestWebhookSettings): QuestWebhookSettings
}

class QuestWebhookRepositoryImpl(
    private val client: HttpClient,
) : QuestWebhookRepository {
    override suspend fun getSettings(): QuestWebhookSettings = client.get("/api/settings/quest-webhook").body()

    override suspend fun updateSettings(settings: QuestWebhookSettings): QuestWebhookSettings =
        client
            .put("/api/settings/quest-webhook") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
}
