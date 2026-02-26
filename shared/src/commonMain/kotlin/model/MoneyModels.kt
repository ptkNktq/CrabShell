package model

import kotlinx.serialization.Serializable

@Serializable
data class Payment(
    val uid: String,
    val amount: Long,
)

@Serializable
data class PaymentRecord(
    val uid: String,
    val amount: Long,
    val paidAt: String,
    val note: String = "",
    val isRedemption: Boolean = false,
)

@Serializable
data class MoneyItem(
    val id: String,
    val name: String,
    val amount: Long,
    val note: String = "",
    val payments: List<Payment> = emptyList(),
    val tags: List<String> = emptyList(),
)

object MoneyTags {
    const val RECURRING = "毎月"
}

@Serializable
data class MonthlyMoney(
    val month: String,
    val items: List<MoneyItem> = emptyList(),
    val paymentRecords: List<PaymentRecord> = emptyList(),
    val locked: Boolean = false,
)
