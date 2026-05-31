package server.money

import model.MonthlyMoney

fun MonthlyMoney.filterForUser(uid: String): MonthlyMoney {
    val userItems = items.filter { item -> item.shares.any { it.uid == uid } }
    val userPayments = payments.filter { it.uid == uid }
    return MonthlyMoney(yearMonth = yearMonth, items = userItems, payments = userPayments, status = status)
}
