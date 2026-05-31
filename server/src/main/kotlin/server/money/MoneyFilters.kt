package server.money

import server.money.model.MonthlyMoneyRecord

fun MonthlyMoneyRecord.filterForUser(uid: String): MonthlyMoneyRecord {
    val userItems = items.filter { item -> item.payments.any { it.uid == uid } }
    val userRecords = paymentRecords.filter { it.uid == uid }
    return MonthlyMoneyRecord(yearMonth = yearMonth, items = userItems, paymentRecords = userRecords, status = status)
}
