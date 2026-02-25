package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import model.CacheRefreshResult

interface CacheRepository {
    suspend fun clearServerCache(): CacheRefreshResult
}

class CacheRepositoryImpl(
    private val client: HttpClient,
) : CacheRepository {
    override suspend fun clearServerCache(): CacheRefreshResult = client.post("/api/admin/cache/clear").body()
}
