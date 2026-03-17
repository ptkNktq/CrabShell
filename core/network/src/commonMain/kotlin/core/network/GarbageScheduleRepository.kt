package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.GarbageNotificationSettings
import model.GarbageTypeSchedule

interface GarbageScheduleRepository {
    suspend fun getSchedules(): List<GarbageTypeSchedule>

    suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>)

    suspend fun getNotificationSettings(): GarbageNotificationSettings

    suspend fun saveNotificationSettings(settings: GarbageNotificationSettings)
}

class GarbageScheduleRepositoryImpl(
    private val client: HttpClient,
) : GarbageScheduleRepository {
    override suspend fun getSchedules(): List<GarbageTypeSchedule> = client.get("/api/garbage/schedule").body()

    override suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>) {
        client.put("/api/garbage/schedule") {
            contentType(ContentType.Application.Json)
            setBody(schedules)
        }
    }

    override suspend fun getNotificationSettings(): GarbageNotificationSettings = client.get("/api/garbage/notification-settings").body()

    override suspend fun saveNotificationSettings(settings: GarbageNotificationSettings) {
        client.put("/api/garbage/notification-settings") {
            contentType(ContentType.Application.Json)
            setBody(settings)
        }
    }
}
