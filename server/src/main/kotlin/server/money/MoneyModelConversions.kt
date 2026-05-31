package server.money

import model.MoneyItemResponse
import model.MoneyItemSaveRequest
import model.MonthlyMoneyResponse
import model.MonthlyMoneySaveRequest
import model.PaymentRecordResponse
import model.PaymentRecordSaveRequest
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
// status と yearMonth は body には含まれないため、呼び出し側で別途渡す。
// ---------------------------------------------------------------------------------

fun MonthlyMoneySaveRequest.toRecord(
    yearMonth: String,
    status: model.MonthlyMoneyStatus,
): MonthlyMoneyRecord =
    MonthlyMoneyRecord(
        yearMonth = yearMonth,
        items = items.map { it.toRecord() },
        paymentRecords = paymentRecords.map { it.toRecord() },
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

fun PaymentRecordSaveRequest.toRecord(): PaymentRecord =
    PaymentRecord(
        uid = uid,
        amount = amount,
        paidAt = paidAt,
        note = note,
        isRedemption = isRedemption,
    )
