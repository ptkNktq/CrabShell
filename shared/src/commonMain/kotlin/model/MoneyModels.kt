package model

import kotlinx.serialization.Serializable

/**
 * 月次お金データのステータス。
 *
 * 永続化レコード（`server.money.model.MonthlyMoneyRecord`）と API レスポンス
 * （`MonthlyMoneyResponse`）の双方で共有される値。
 */
@Serializable
enum class MonthlyMoneyStatus {
    /** 支払い内容を組み立て中。ユーザーには「確定前」として表示する。 */
    PENDING,

    /** 支払い内容が確定済み。ユーザーへの告知目的のみで、操作制約は掛からない。 */
    CONFIRMED,

    /** 月跨ぎ等で凍結済み。項目編集・支払い記録のすべてを拒否する。 */
    FROZEN,
}

/** タグ識別子（`MoneyItemRecord` / `MoneyItemResponse` / `MoneyItemSaveRequest` の tags に格納される文字列）。永続化・API の両層で共通。 */
object MoneyTags {
    const val RECURRING = "毎月"
    const val CARRY_OVER = "繰越"
}

// =================================================================================================
// Response DTO
//   - サーバー → クライアントへの API 応答
//   - 永続化レコード（`server.money.model.~Record`）とは別型として shared に置く
// =================================================================================================

@Serializable
data class MonthlyMoneyResponse(
    val yearMonth: String,
    val items: List<MoneyItemResponse> = emptyList(),
    val paymentRecords: List<PaymentRecordResponse> = emptyList(),
    val status: MonthlyMoneyStatus = MonthlyMoneyStatus.PENDING,
)

@Serializable
data class MoneyItemResponse(
    val id: String,
    val name: String,
    val amount: Long,
    val note: String = "",
    val payments: List<PaymentResponse> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class PaymentResponse(
    val uid: String,
    val amount: Long,
)

@Serializable
data class PaymentRecordResponse(
    val uid: String,
    val amount: Long,
    val paidAt: String,
    val note: String = "",
    val isRedemption: Boolean = false,
)

// =================================================================================================
// Request DTO
//   - クライアント → サーバーへの API 要求
//   - サーバーが決定するフィールド（uid, isRedemption 等）はクライアントから受け取らない
// =================================================================================================

/**
 * `POST /api/money/{yearMonth}/pay` のリクエスト DTO。
 *
 * クライアントから受け取るのは `amount` のみ。
 * - `uid` は principal から取得
 * - `isRedemption` は `/pay` 経路では常に false（精算は `/report/balances/redeem` 経由のみ）
 * - `paidAt` はサーバー側で `Instant.now().toString()` を生成（クライアント時計依存・改ざんを排除、
 *   `/report/balances/redeem` と対称な設計）
 */
@Serializable
data class PayRequest(
    val amount: Long,
)

/**
 * `PUT /api/money/{yearMonth}` のリクエスト DTO。
 *
 * - `status` は本エンドポイントで変更しない（`PATCH /status` 専用）ため受け取らない。
 * - `paymentRecords` は意図的に含めない: クライアント (`MoneyViewModel`) は items のみ編集する。
 *   PUT 経路で paymentRecords を露出させると、admin が他ユーザーの uid と `isRedemption=true`
 *   を組み合わせた精算レコードを偽装でき、`BalanceCalculationService` の過払い計算が崩れる。
 *   入金は `POST /pay` 経由でのみ追加、精算は `POST /report/balances/redeem` 経由でのみ追加。
 * - `yearMonth` はパスパラメータから取得するため body には含めない。
 */
@Serializable
data class MonthlyMoneySaveRequest(
    val items: List<MoneyItemSaveRequest> = emptyList(),
)

@Serializable
data class MoneyItemSaveRequest(
    val id: String,
    val name: String,
    val amount: Long,
    val note: String = "",
    val payments: List<PaymentSaveRequest> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class PaymentSaveRequest(
    val uid: String,
    val amount: Long,
)

/** `PATCH /api/money/{yearMonth}/status` のリクエスト DTO。 */
@Serializable
data class MonthlyMoneyStatusUpdateRequest(
    val status: MonthlyMoneyStatus,
)

// =================================================================================================
// Response → SaveRequest 変換ヘルパ
//
// クライアントは get* で受け取った Response を編集して save* で送る運用のため、
// 構造的に同型な Request 型へ変換するための薄いマッピングを用意する。
// =================================================================================================

fun MonthlyMoneyResponse.toSaveRequest(): MonthlyMoneySaveRequest =
    MonthlyMoneySaveRequest(
        items = items.map { it.toSaveRequest() },
    )

fun MoneyItemResponse.toSaveRequest(): MoneyItemSaveRequest =
    MoneyItemSaveRequest(
        id = id,
        name = name,
        amount = amount,
        note = note,
        payments = payments.map { it.toSaveRequest() },
        tags = tags,
    )

fun PaymentResponse.toSaveRequest(): PaymentSaveRequest = PaymentSaveRequest(uid = uid, amount = amount)

// =================================================================================================
// Webhook 設定（API request + response として共用、admin only エンドポイント）
//
// 構造は同型だが、Firestore 保存先と通知タイミングが異なるため別型として定義している。
// 共通化は Issue #204（WebhookService 抽象化）と合わせて検討する。
// =================================================================================================

@Serializable
data class MoneyWebhookSettings(
    val url: String = "",
    val enabled: Boolean = false,
    val message: String = "",
)

@Serializable
data class PaymentWebhookSettings(
    val url: String = "",
    val enabled: Boolean = false,
    val message: String = "",
)
