package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.MoneyDueDateNotificationSettings

interface MoneyDueDateNotificationRepository {
    suspend fun getSettings(): MoneyDueDateNotificationSettings

    suspend fun updateSettings(settings: MoneyDueDateNotificationSettings): MoneyDueDateNotificationSettings
}

class MoneyDueDateNotificationRepositoryImpl(
    private val client: HttpClient,
) : MoneyDueDateNotificationRepository {
    override suspend fun getSettings(): MoneyDueDateNotificationSettings = client.get("/api/money/due-date-notification-settings").body()

    override suspend fun updateSettings(settings: MoneyDueDateNotificationSettings): MoneyDueDateNotificationSettings =
        client
            .put("/api/money/due-date-notification-settings") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
}
