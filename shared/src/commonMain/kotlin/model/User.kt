package model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val isAdmin: Boolean = false,
)

@Serializable
data class UpdateDisplayNameRequest(
    val displayName: String,
)
