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
    const val CARRY_OVER = "繰越"
}

@Serializable
enum class MonthlyMoneyStatus {
    /** 支払い内容を組み立て中。ユーザーには「確定前」として表示する。 */
    PENDING,

    /** 支払い内容が確定済み。ユーザーへの告知目的のみで、操作制約は掛からない。 */
    CONFIRMED,

    /** 月跨ぎ等で凍結済み。項目編集・支払い記録のすべてを拒否する。 */
    FROZEN,
}

@Serializable
data class MonthlyMoney(
    val yearMonth: String,
    val items: List<MoneyItem> = emptyList(),
    val paymentRecords: List<PaymentRecord> = emptyList(),
    val status: MonthlyMoneyStatus = MonthlyMoneyStatus.PENDING,
)

@Serializable
data class MonthlyMoneyStatusUpdate(
    val status: MonthlyMoneyStatus,
)
