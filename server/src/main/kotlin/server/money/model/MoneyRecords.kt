package server.money.model

import model.MonthlyMoneyStatus

/**
 * 月次お金データの永続化レコード。
 *
 * 3 層分離方針:
 * - Request DTO (`shared/`): クライアントが送るフィールドのみ露出
 * - Response DTO (`shared/`): クライアントに返すフィールドのみ露出
 * - Record（本ファイル, `server/`）: 永続化レイヤ・サーバードメイン用。クライアントには見えない
 *
 * Firestore とのマッピングは [server.money.FirestoreMoneyRepository] が手書きで行うため、
 * Record 型自体に `@Serializable` は不要。
 */
data class MonthlyMoneyRecord(
    val yearMonth: String,
    val items: List<MoneyItemRecord> = emptyList(),
    val paymentRecords: List<PaymentRecord> = emptyList(),
    val status: MonthlyMoneyStatus = MonthlyMoneyStatus.PENDING,
)

data class MoneyItemRecord(
    val id: String,
    val name: String,
    val amount: Long,
    val note: String = "",
    val payments: List<Payment> = emptyList(),
    val tags: List<String> = emptyList(),
)

/** MoneyItemRecord 内に埋め込まれる「各ユーザーが負担する金額」を表すバリュー。 */
data class Payment(
    val uid: String,
    val amount: Long,
)

/**
 * 月内の支払い活動を記録するレコード。
 * 通常入金（`/pay`）と過払い精算（`/report/balances/redeem`、isRedemption=true）の両方を表現する。
 */
data class PaymentRecord(
    val uid: String,
    val amount: Long,
    val paidAt: String,
    val note: String = "",
    val isRedemption: Boolean = false,
)
