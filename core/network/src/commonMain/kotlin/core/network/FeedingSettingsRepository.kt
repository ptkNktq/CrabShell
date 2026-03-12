package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.FeedingSettings

interface FeedingSettingsRepository {
    suspend fun getSettings(): FeedingSettings

    suspend fun updateSettings(settings: FeedingSettings): FeedingSettings
}

class FeedingSettingsRepositoryImpl(
    private val client: HttpClient,
) : FeedingSettingsRepository {
    override suspend fun getSettings(): FeedingSettings = client.get("/api/feeding/settings").body()

    override suspend fun updateSettings(settings: FeedingSettings): FeedingSettings =
        client
            .put("/api/feeding/settings") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
}
