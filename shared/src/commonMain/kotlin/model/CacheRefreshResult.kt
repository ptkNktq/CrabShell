package model

import kotlinx.serialization.Serializable

@Serializable
data class CacheRefreshResult(
    val clearedCaches: List<String>,
    val message: String,
)
