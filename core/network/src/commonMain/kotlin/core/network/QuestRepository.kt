package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.CreateQuestRequest
import model.GenerateQuestTextRequest
import model.GenerateQuestTextResponse
import model.Quest
import model.QuestCategory
import model.QuestStatus

interface QuestRepository {
    suspend fun getQuests(status: QuestStatus? = null): List<Quest>

    suspend fun createQuest(request: CreateQuestRequest): Quest

    suspend fun acceptQuest(id: String): Quest

    suspend fun verifyQuest(id: String): Quest

    suspend fun deleteQuest(id: String)

    suspend fun isAiAvailable(): Boolean

    suspend fun generateQuestText(
        title: String,
        category: QuestCategory,
        rewardPoints: Int,
        deadline: String?,
    ): String
}

class QuestRepositoryImpl(
    private val client: HttpClient,
) : QuestRepository {
    override suspend fun getQuests(status: QuestStatus?): List<Quest> =
        client
            .get("/api/quests") {
                status?.let { parameter("status", it.name) }
            }.body()

    override suspend fun createQuest(request: CreateQuestRequest): Quest =
        client
            .post("/api/quests") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun acceptQuest(id: String): Quest = client.put("/api/quests/$id/accept").body()

    override suspend fun verifyQuest(id: String): Quest = client.put("/api/quests/$id/verify").body()

    override suspend fun deleteQuest(id: String) {
        client.delete("/api/quests/$id")
    }

    override suspend fun isAiAvailable(): Boolean =
        try {
            client
                .get("/api/quests/ai-available")
                .body<Map<String, Boolean>>()["available"] == true
        } catch (_: Exception) {
            false
        }

    override suspend fun generateQuestText(
        title: String,
        category: QuestCategory,
        rewardPoints: Int,
        deadline: String?,
    ): String =
        client
            .post("/api/quests/generate-text") {
                contentType(ContentType.Application.Json)
                setBody(
                    GenerateQuestTextRequest(
                        title = title,
                        category = category,
                        rewardPoints = rewardPoints,
                        deadline = deadline,
                    ),
                )
            }.body<GenerateQuestTextResponse>()
            .generatedText
}
