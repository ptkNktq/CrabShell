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
    val yearMonth: String,
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

@Serializable
data class BalanceSummary(
    val balances: List<UserBalance> = emptyList(),
    val periodStart: String = "",
    val periodEnd: String = "",
)

@Serializable
data class OverpaymentRedemptionRequest(
    val uid: String,
    val yearMonth: String,
    val amount: Long,
    val note: String = "",
)
