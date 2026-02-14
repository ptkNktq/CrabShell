package core.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.Feeding
import model.FeedingLog
import model.MealTime

object FeedingRepository {
    suspend fun getFeedingLog(
        petId: String,
        date: String,
    ): FeedingLog = authenticatedClient.get("/api/pets/$petId/feeding/$date").body()

    suspend fun feed(
        petId: String,
        date: String,
        mealTime: MealTime,
    ): Feeding =
        authenticatedClient.put(
            "/api/pets/$petId/feeding/$date/${mealTime.name.lowercase()}",
        ).body()

    suspend fun updateNote(
        petId: String,
        date: String,
        note: String,
    ) {
        authenticatedClient.put("/api/pets/$petId/feeding/$date/note") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("note" to note))
        }
    }
}
