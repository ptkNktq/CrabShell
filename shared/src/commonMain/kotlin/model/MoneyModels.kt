package model

import kotlinx.serialization.Serializable

@Serializable
data class PaymentRecord(val amount: Long, val paidAt: String)

@Serializable
data class Payment(val uid: String, val amount: Long, val records: List<PaymentRecord> = emptyList())

@Serializable
data class MoneyItem(
    val id: String,
    val name: String,
    val amount: Long,
    val note: String = "",
    val payments: List<Payment> = emptyList(),
    val recurring: Boolean = false,
)

@Serializable
data class MonthlyMoney(
    val month: String,
    val items: List<MoneyItem> = emptyList(),
)
