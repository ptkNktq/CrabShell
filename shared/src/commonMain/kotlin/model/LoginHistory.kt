package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LoginMethod {
    @SerialName("email")
    EMAIL,

    @SerialName("passkey")
    PASSKEY,
}

@Serializable
data class LoginEvent(
    val id: String = "",
    val timestamp: String = "",
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val loginMethod: LoginMethod? = null,
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
    val loginMethod: LoginMethod,
)
