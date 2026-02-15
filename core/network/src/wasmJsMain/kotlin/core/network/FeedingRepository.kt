package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.Feeding
import model.FeedingLog
import model.MealTime

interface FeedingRepository {
    suspend fun getFeedingLog(
        petId: String,
        date: String,
    ): FeedingLog

    suspend fun feed(
        petId: String,
        date: String,
        mealTime: MealTime,
    ): Feeding

    suspend fun updateNote(
        petId: String,
        date: String,
        note: String,
    )
}

class FeedingRepositoryImpl(private val client: HttpClient) : FeedingRepository {
    override suspend fun getFeedingLog(
        petId: String,
        date: String,
    ): FeedingLog = client.get("/api/pets/$petId/feeding/$date").body()

    override suspend fun feed(
        petId: String,
        date: String,
        mealTime: MealTime,
    ): Feeding =
        client.put(
            "/api/pets/$petId/feeding/$date/${mealTime.name.lowercase()}",
        ).body()

    override suspend fun updateNote(
        petId: String,
        date: String,
        note: String,
    ) {
        client.put("/api/pets/$petId/feeding/$date/note") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("note" to note))
        }
    }
}
