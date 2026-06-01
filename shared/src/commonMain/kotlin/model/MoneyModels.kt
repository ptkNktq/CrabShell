package model

import kotlinx.serialization.Serializable

/**
 * 月次お金データのステータス。
 * 永続化（shared 直接）と API レスポンスの双方で共有される値。
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

/** タグ識別子。`MoneyItem.tags` 等に格納される文字列。 */
object MoneyTags {
    const val RECURRING = "毎月"
    const val CARRY_OVER = "繰越"
}

// =================================================================================================
// ドメインモデル
//   - 永続化 + API レスポンスの兼用型。
//   - サーバー側ロジック・Firestore マッピング・クライアント UI 状態の全てで使う。
//   - 詳細な配置方針は README「API 設計」セクションを参照。
// =================================================================================================

@Serializable
data class MonthlyMoney(
    val yearMonth: String,
    val items: List<MoneyItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val status: MonthlyMoneyStatus = MonthlyMoneyStatus.PENDING,
)

@Serializable
data class MoneyItem(
    val id: String,
    val name: String,
    val amount: Long,
    val note: String = "",
    val shares: List<Share> = emptyList(),
    val tags: List<String> = emptyList(),
)

/** 項目ごとの負担分担。「このユーザーはこの項目でこの金額を負担する」を表す。 */
@Serializable
data class Share(
    val uid: String,
    val amount: Long,
)

/**
 * 実際の振込記録。「このユーザーがいつこの金額を振り込んだ」を表す。
 * isRedemption=true は過払い金からの精算（残債計算で `redeemed` 側に加算される）。
 */
@Serializable
data class Payment(
    val uid: String,
    val amount: Long,
    val paidAt: String,
    val note: String = "",
    val isRedemption: Boolean = false,
)

// =================================================================================================
// Request DTO
//   - クライアント → サーバーへの API 要求。
//   - サーバーが決定するフィールド（uid, isRedemption, paidAt 等）はクライアントから受け取らない。
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
 * - `payments` は意図的に含めない: クライアント (`MoneyViewModel`) は items のみ編集する。
 *   PUT 経路で payments を露出させると、admin が他ユーザーの uid と `isRedemption=true`
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
    val shares: List<ShareSaveRequest> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class ShareSaveRequest(
    val uid: String,
    val amount: Long,
)

/** `PATCH /api/money/{yearMonth}/status` のリクエスト DTO。 */
@Serializable
data class MonthlyMoneyStatusUpdateRequest(
    val status: MonthlyMoneyStatus,
)

// =================================================================================================
// ドメインモデル → SaveRequest 変換ヘルパ
//
// クライアントは get* で受け取ったドメインモデル状態を編集して save* で送る運用のため、
// SaveRequest 型へ変換するための薄いマッピングを用意する。
// =================================================================================================

fun MonthlyMoney.toSaveRequest(): MonthlyMoneySaveRequest =
    MonthlyMoneySaveRequest(
        items = items.map { it.toSaveRequest() },
    )

fun MoneyItem.toSaveRequest(): MoneyItemSaveRequest =
    MoneyItemSaveRequest(
        id = id,
        name = name,
        amount = amount,
        note = note,
        shares = shares.map { it.toSaveRequest() },
        tags = tags,
    )

fun Share.toSaveRequest(): ShareSaveRequest = ShareSaveRequest(uid = uid, amount = amount)

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
