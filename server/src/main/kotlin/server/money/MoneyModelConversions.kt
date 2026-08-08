/**
 * Money API の Request DTO → ドメインモデル変換ヘルパ集。
 *
 * 2 層分離方針（README「API 設計」セクション参照）:
 * - Request DTO (`shared/`、`~Request` / `~SaveRequest`): クライアント → サーバー
 * - ドメインモデル (`shared/`、`MonthlyMoney` / `MoneyItem` / `Share` / `Payment`):
 *   永続化 + API レスポンスを兼ねる
 *
 * ルートハンドラは `~Request.toDomain()` でサーバー側組み立てに必要な追加情報（uid, status,
 * paidAt, 既存 payments 等）を渡してドメインモデルを構築する。
 */
package server.money

import model.MoneyItem
import model.MoneyItemSaveRequest
import model.MonthlyMoney
import model.MonthlyMoneySaveRequest
import model.MonthlyMoneyStatus
import model.Payment
import model.Share
import model.ShareSaveRequest

// ---------------------------------------------------------------------------------
// Request → ドメインモデル（クライアント受信 → サーバー組み立て）
//
// `MonthlyMoneySaveRequest` は items のみを持ち、payments / status / yearMonth は
// 含まないため、呼び出し側で別途渡す。payments は PUT 経路で改ざんさせないために
// 既存値をそのまま温存する設計（precondition: 既存値は repository から取得済み）。
// ---------------------------------------------------------------------------------

fun MonthlyMoneySaveRequest.toDomain(
    yearMonth: String,
    status: MonthlyMoneyStatus,
    existingPayments: List<Payment>,
): MonthlyMoney =
    MonthlyMoney(
        yearMonth = yearMonth,
        items = items.map { it.toDomain() },
        payments = existingPayments,
        status = status,
    )

fun MoneyItemSaveRequest.toDomain(): MoneyItem =
    MoneyItem(
        id = id,
        name = name,
        amount = amount,
        note = note,
        shares = shares.map { it.toDomain() },
        tags = tags,
        dueDate = dueDate,
    )

fun ShareSaveRequest.toDomain(): Share = Share(uid = uid, amount = amount)
