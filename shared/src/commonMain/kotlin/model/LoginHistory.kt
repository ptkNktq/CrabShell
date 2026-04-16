package model

import kotlinx.serialization.Serializable

@Serializable
data class LoginEvent(
    val id: String = "",
    val timestamp: String = "",
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val loginMethod: String? = null,
    // Phase 2: IP Geolocation
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    // Phase 2: 不審ログイン検知
    val suspicious: Boolean? = null,
    val deviceFingerprint: String? = null,
)

@Serializable
data class RecordLoginRequest(
    val loginMethod: String,
)
