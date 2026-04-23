package server.money

import model.MoneyItem
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.Payment
import model.PaymentRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFiltersTest {
    @Test
    fun filterKeepsOnlyUserItems() {
        val data =
            MonthlyMoney(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "item1",
                            name = "Rent",
                            amount = 10000L,
                            payments =
                                listOf(
                                    Payment(uid = "u1", amount = 5000L),
                                    Payment(uid = "u2", amount = 5000L),
                                ),
                        ),
                        MoneyItem(
                            id = "item2",
                            name = "Insurance",
                            amount = 3000L,
                            payments = listOf(Payment(uid = "u2", amount = 3000L)),
                        ),
                    ),
                paymentRecords =
                    listOf(
                        PaymentRecord(uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                        PaymentRecord(uid = "u2", amount = 8000L, paidAt = "2024-06-01"),
                    ),
            )

        val filtered = data.filterForUser("u1")
        assertEquals("2024-06", filtered.yearMonth)
        assertEquals(1, filtered.items.size)
        assertEquals("item1", filtered.items[0].id)
        assertEquals(1, filtered.paymentRecords.size)
        assertEquals("u1", filtered.paymentRecords[0].uid)
    }

    @Test
    fun filterPreservesStatus() {
        for (status in MonthlyMoneyStatus.entries) {
            val data =
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items = emptyList(),
                    paymentRecords = emptyList(),
                    status = status,
                )
            val filtered = data.filterForUser("u1")
            assertEquals(status, filtered.status)
        }
    }

    @Test
    fun filterReturnsEmptyForUnknownUser() {
        val data =
            MonthlyMoney(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "item1",
                            name = "Rent",
                            amount = 10000L,
                            payments = listOf(Payment(uid = "u1", amount = 10000L)),
                        ),
                    ),
                paymentRecords =
                    listOf(
                        PaymentRecord(uid = "u1", amount = 10000L, paidAt = "2024-06-01"),
                    ),
            )
        val filtered = data.filterForUser("unknown")
        assertEquals(0, filtered.items.size)
        assertEquals(0, filtered.paymentRecords.size)
    }

    @Test
    fun filterKeepsItemIfUserHasAnyPayment() {
        val data =
            MonthlyMoney(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "item1",
                            name = "Rent",
                            amount = 10000L,
                            payments =
                                listOf(
                                    Payment(uid = "u1", amount = 3000L),
                                    Payment(uid = "u2", amount = 7000L),
                                ),
                        ),
                    ),
            )
        val filtered = data.filterForUser("u1")
        assertEquals(1, filtered.items.size)
        // item は全 payments を保持（対象ユーザー分だけでなく全員分）
        assertEquals(2, filtered.items[0].payments.size)
    }

    @Test
    fun filterKeepsRedemptionRecordsForUser() {
        // isRedemption=true の精算レコードも uid ベースで正しくフィルタされる
        val data =
            MonthlyMoney(
                yearMonth = "2024-06",
                paymentRecords =
                    listOf(
                        PaymentRecord(uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                        PaymentRecord(uid = "u1", amount = 1000L, paidAt = "2024-06-15", isRedemption = true),
                        PaymentRecord(uid = "u2", amount = 3000L, paidAt = "2024-06-01", isRedemption = true),
                    ),
            )
        val filtered = data.filterForUser("u1")
        assertEquals(2, filtered.paymentRecords.size)
        assertEquals(true, filtered.paymentRecords[1].isRedemption)
    }
}
