package model

import kotlinx.serialization.Serializable

@Serializable
data class UserPoints(
    val uid: String = "",
    val displayName: String = "",
    val balance: Int = 0,
)

@Serializable
data class PointHistory(
    val id: String = "",
    val uid: String = "",
    val amount: Int = 0,
    val reason: String = "",
    val questId: String? = null,
    val rewardId: String? = null,
    val timestamp: String = "",
)

@Serializable
data class Reward(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val cost: Int = 0,
    val isAvailable: Boolean = true,
    val creatorUid: String = "",
)

@Serializable
data class CreateRewardRequest(
    val name: String,
    val description: String = "",
    val cost: Int,
)
