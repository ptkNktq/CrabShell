package model

import kotlinx.serialization.Serializable

@Serializable
data class DashboardItem(
    val id: Int,
    val title: String,
    val description: String,
    val status: Status = Status.ACTIVE,
)

@Serializable
enum class Status {
    ACTIVE,
    INACTIVE,
    PENDING,
}
