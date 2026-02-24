package server.money

import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.PaymentRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFiltersTest {
    @Test
    fun filterKeepsOnlyUserItems() {
        val data =
            MonthlyMoney(
                month = "2024-06",
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
        assertEquals("2024-06", filtered.month)
        assertEquals(1, filtered.items.size)
        assertEquals("item1", filtered.items[0].id)
        assertEquals(1, filtered.paymentRecords.size)
        assertEquals("u1", filtered.paymentRecords[0].uid)
    }

    @Test
    fun filterPreservesLockedState() {
        val data =
            MonthlyMoney(
                month = "2024-06",
                items = emptyList(),
                paymentRecords = emptyList(),
                locked = true,
            )
        val filtered = data.filterForUser("u1")
        assertEquals(true, filtered.locked)
    }

    @Test
    fun filterReturnsEmptyForUnknownUser() {
        val data =
            MonthlyMoney(
                month = "2024-06",
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
                month = "2024-06",
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
        // Item is kept with all payments (not just the user's)
        assertEquals(2, filtered.items[0].payments.size)
    }
}
