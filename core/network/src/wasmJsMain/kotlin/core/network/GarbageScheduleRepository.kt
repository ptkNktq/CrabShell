package core.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.GarbageTypeSchedule

object GarbageScheduleRepository {
    suspend fun getSchedules(): List<GarbageTypeSchedule> = authenticatedClient.get("/api/garbage/schedule").body()

    suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>) {
        authenticatedClient.put("/api/garbage/schedule") {
            contentType(ContentType.Application.Json)
            setBody(schedules)
        }
    }
}
