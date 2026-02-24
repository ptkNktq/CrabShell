package server.money

import model.MonthlyMoney

fun MonthlyMoney.filterForUser(uid: String): MonthlyMoney {
    val userItems = items.filter { item -> item.payments.any { it.uid == uid } }
    val userRecords = paymentRecords.filter { it.uid == uid }
    return MonthlyMoney(month = month, items = userItems, paymentRecords = userRecords, locked = locked)
}
