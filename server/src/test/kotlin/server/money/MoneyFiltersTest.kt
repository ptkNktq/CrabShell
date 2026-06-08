package server.money

import model.MoneyItem
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.Payment
import model.Share
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
                            shares =
                                listOf(
                                    Share(uid = "u1", amount = 5000L),
                                    Share(uid = "u2", amount = 5000L),
                                ),
                        ),
                        MoneyItem(
                            id = "item2",
                            name = "Insurance",
                            amount = 3000L,
                            shares = listOf(Share(uid = "u2", amount = 3000L)),
                        ),
                    ),
                payments =
                    listOf(
                        Payment(id = "test-id", uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                        Payment(id = "test-id", uid = "u2", amount = 8000L, paidAt = "2024-06-01"),
                    ),
            )

        val filtered = data.filterForUser("u1")
        assertEquals("2024-06", filtered.yearMonth)
        assertEquals(1, filtered.items.size)
        assertEquals("item1", filtered.items[0].id)
        assertEquals(1, filtered.payments.size)
        assertEquals("u1", filtered.payments[0].uid)
    }

    @Test
    fun filterPreservesStatus() {
        for (status in MonthlyMoneyStatus.entries) {
            val data =
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items = emptyList(),
                    payments = emptyList(),
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
                            shares = listOf(Share(uid = "u1", amount = 10000L)),
                        ),
                    ),
                payments =
                    listOf(
                        Payment(id = "test-id", uid = "u1", amount = 10000L, paidAt = "2024-06-01"),
                    ),
            )
        val filtered = data.filterForUser("unknown")
        assertEquals(0, filtered.items.size)
        assertEquals(0, filtered.payments.size)
    }

    @Test
    fun filterKeepsItemIfUserHasAnyShare() {
        val data =
            MonthlyMoney(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "item1",
                            name = "Rent",
                            amount = 10000L,
                            shares =
                                listOf(
                                    Share(uid = "u1", amount = 3000L),
                                    Share(uid = "u2", amount = 7000L),
                                ),
                        ),
                    ),
            )
        val filtered = data.filterForUser("u1")
        assertEquals(1, filtered.items.size)
        // item は全 shares を保持（対象ユーザー分だけでなく全員分）
        assertEquals(2, filtered.items[0].shares.size)
    }

    @Test
    fun filterKeepsRedemptionPaymentsForUser() {
        // isRedemption=true の精算レコードも uid ベースで正しくフィルタされる
        val data =
            MonthlyMoney(
                yearMonth = "2024-06",
                payments =
                    listOf(
                        Payment(id = "test-id", uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                        Payment(id = "test-id", uid = "u1", amount = 1000L, paidAt = "2024-06-15", isRedemption = true),
                        Payment(id = "test-id", uid = "u2", amount = 3000L, paidAt = "2024-06-01", isRedemption = true),
                    ),
            )
        val filtered = data.filterForUser("u1")
        assertEquals(2, filtered.payments.size)
        assertEquals(true, filtered.payments[1].isRedemption)
    }
}
