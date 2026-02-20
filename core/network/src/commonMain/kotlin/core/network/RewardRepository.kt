package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.CreateRewardRequest
import model.Reward

interface RewardRepository {
    suspend fun getRewards(): List<Reward>

    suspend fun createReward(request: CreateRewardRequest): Reward

    suspend fun updateReward(
        id: String,
        request: CreateRewardRequest,
    ): Reward

    suspend fun deleteReward(id: String)

    suspend fun exchangeReward(id: String)
}

class RewardRepositoryImpl(
    private val client: HttpClient,
) : RewardRepository {
    override suspend fun getRewards(): List<Reward> = client.get("/api/rewards").body()

    override suspend fun createReward(request: CreateRewardRequest): Reward =
        client
            .post("/api/rewards") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun updateReward(
        id: String,
        request: CreateRewardRequest,
    ): Reward =
        client
            .put("/api/rewards/$id") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun deleteReward(id: String) {
        client.delete("/api/rewards/$id")
    }

    override suspend fun exchangeReward(id: String) {
        client.post("/api/rewards/$id/exchange")
    }
}
