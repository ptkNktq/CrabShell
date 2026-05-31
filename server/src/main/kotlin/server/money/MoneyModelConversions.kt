/**
 * Money ドメインの Record（永続化）⇔ Request/Response（API DTO）変換ヘルパ集。
 *
 * サーバー側専用。クライアントから Record 型は参照できない（`server/money/model/`）。
 * ルートハンドラはここでの拡張関数を経由して受信値を Record に詰め、返却値を Response に
 * マッピングする。
 */
package server.money

import model.MoneyItemResponse
import model.MoneyItemSaveRequest
import model.MonthlyMoneyResponse
import model.MonthlyMoneySaveRequest
import model.MonthlyMoneyStatus
import model.PaymentRecordResponse
import model.PaymentResponse
import model.PaymentSaveRequest
import server.money.model.MoneyItemRecord
import server.money.model.MonthlyMoneyRecord
import server.money.model.Payment
import server.money.model.PaymentRecord

// ---------------------------------------------------------------------------------
// Record → Response（サーバー内部 → クライアント返却）
// ---------------------------------------------------------------------------------

fun MonthlyMoneyRecord.toResponse(): MonthlyMoneyResponse =
    MonthlyMoneyResponse(
        yearMonth = yearMonth,
        items = items.map { it.toResponse() },
        paymentRecords = paymentRecords.map { it.toResponse() },
        status = status,
    )

fun MoneyItemRecord.toResponse(): MoneyItemResponse =
    MoneyItemResponse(
        id = id,
        name = name,
        amount = amount,
        note = note,
        payments = payments.map { it.toResponse() },
        tags = tags,
    )

fun Payment.toResponse(): PaymentResponse = PaymentResponse(uid = uid, amount = amount)

fun PaymentRecord.toResponse(): PaymentRecordResponse =
    PaymentRecordResponse(
        uid = uid,
        amount = amount,
        paidAt = paidAt,
        note = note,
        isRedemption = isRedemption,
    )

// ---------------------------------------------------------------------------------
// Request → Record（クライアント受信 → サーバー組み立て）
//
// `MonthlyMoneySaveRequest` は items のみを持ち、paymentRecords / status / yearMonth は
// 含まないため、呼び出し側で別途渡す。paymentRecords は PUT 経路で改ざんさせないために
// 既存レコードをそのまま温存する設計（precondition: 既存値は repository から取得済み）。
// ---------------------------------------------------------------------------------

fun MonthlyMoneySaveRequest.toRecord(
    yearMonth: String,
    status: MonthlyMoneyStatus,
    existingPaymentRecords: List<PaymentRecord>,
): MonthlyMoneyRecord =
    MonthlyMoneyRecord(
        yearMonth = yearMonth,
        items = items.map { it.toRecord() },
        paymentRecords = existingPaymentRecords,
        status = status,
    )

fun MoneyItemSaveRequest.toRecord(): MoneyItemRecord =
    MoneyItemRecord(
        id = id,
        name = name,
        amount = amount,
        note = note,
        payments = payments.map { it.toRecord() },
        tags = tags,
    )

fun PaymentSaveRequest.toRecord(): Payment = Payment(uid = uid, amount = amount)
