package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import model.PointHistory
import model.UserPoints

interface PointRepository {
    suspend fun getMyPoints(): UserPoints

    suspend fun getRanking(): List<UserPoints>

    suspend fun getHistory(): List<PointHistory>
}

class PointRepositoryImpl(
    private val client: HttpClient,
) : PointRepository {
    override suspend fun getMyPoints(): UserPoints = client.get("/api/points").body()

    override suspend fun getRanking(): List<UserPoints> = client.get("/api/points/ranking").body()

    override suspend fun getHistory(): List<PointHistory> = client.get("/api/points/history").body()
}
