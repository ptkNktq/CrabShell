package model

import kotlinx.serialization.SerialName
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

/**
 * 月次支払いの状態。
 *
 * 各 enum 値には Firestore / JSON 保存時に使うワイヤ値 [wireValue] を持たせている。
 * Kotlin 側の enum 名称を将来リネームしても [wireValue] を保持すれば既存データは
 * 壊れない。JSON シリアライズも [SerialName] で [wireValue] に揃えているため、
 * Firestore / API / 保存形式の 3 つで表現を一元管理できる。
 */
@Serializable
enum class MonthlyMoneyStatus(
    val wireValue: String,
) {
    /** 支払い内容を組み立て中。ユーザーには「確定前」として表示する。 */
    @SerialName("PENDING")
    PENDING("PENDING"),

    /** 支払い内容が確定済み。ユーザーへの告知目的のみで、操作制約は掛からない。 */
    @SerialName("CONFIRMED")
    CONFIRMED("CONFIRMED"),

    /** 月跨ぎ等で凍結済み。項目編集・支払い記録のすべてを拒否する。 */
    @SerialName("FROZEN")
    FROZEN("FROZEN"),
    ;

    companion object {
        /** ワイヤ値から enum を復元する。未知の値は null を返す。 */
        fun fromWireValue(value: String): MonthlyMoneyStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

@Serializable
data class MonthlyMoney(
    val month: String,
    val items: List<MoneyItem> = emptyList(),
    val paymentRecords: List<PaymentRecord> = emptyList(),
    val status: MonthlyMoneyStatus = MonthlyMoneyStatus.PENDING,
)

/**
 * PATCH /api/money/{month}/status の body。
 * 単一フィールドでも DTO としてラップすることで、API の body 構造を
 * \`{ "status": "..." }\` に統一する（プロジェクト方針: CLAUDE.md 参照）。
 */
@Serializable
data class MonthlyMoneyStatusUpdate(
    val status: MonthlyMoneyStatus,
)
