package model

import kotlinx.serialization.Serializable

@Serializable
data class ExpenseItem(
    val name: String,
    val amount: Long,
    val note: String = "",
)

@Serializable
data class MonthlyExpenseSummary(
    val month: String,
    val totalAmount: Long,
    val items: List<ExpenseItem> = emptyList(),
)

@Serializable
data class ExpenseReport(
    val months: List<MonthlyExpenseSummary> = emptyList(),
)

@Serializable
data class UserBalance(
    val uid: String,
    val displayName: String,
    val allocated: Long,
    val paid: Long,
    val remaining: Long,
)
