package server.report

import model.MoneyItem
import model.MoneyTags
import model.MonthlyMoney
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildExpenseSummaryTest {
    @Test
    fun nullDataReturnsZeroSummary() {
        val result = buildExpenseSummary("2024-06", null)
        assertEquals("2024-06", result.month)
        assertEquals(0L, result.totalAmount)
        assertEquals(emptyList(), result.items)
    }

    @Test
    fun normalItemsAreIncluded() {
        val data =
            MonthlyMoney(
                month = "2024-06",
                items =
                    listOf(
                        MoneyItem(id = "i1", name = "Rent", amount = 80000L),
                        MoneyItem(id = "i2", name = "Utilities", amount = 15000L),
                    ),
            )
        val result = buildExpenseSummary("2024-06", data)
        assertEquals(95000L, result.totalAmount)
        assertEquals(2, result.items.size)
    }

    @Test
    fun carryOverItemsAreExcluded() {
        val data =
            MonthlyMoney(
                month = "2024-06",
                items =
                    listOf(
                        MoneyItem(id = "i1", name = "Rent", amount = 80000L),
                        MoneyItem(
                            id = "i2",
                            name = "前月不足分",
                            amount = 5000L,
                            tags = listOf(MoneyTags.CARRY_OVER),
                        ),
                    ),
            )
        val result = buildExpenseSummary("2024-06", data)
        assertEquals(80000L, result.totalAmount)
        assertEquals(1, result.items.size)
        assertEquals("Rent", result.items[0].name)
    }

    @Test
    fun carryOverWithRecurringTagIsExcluded() {
        val data =
            MonthlyMoney(
                month = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "i1",
                            name = "繰越項目",
                            amount = 10000L,
                            tags = listOf(MoneyTags.RECURRING, MoneyTags.CARRY_OVER),
                        ),
                    ),
            )
        val result = buildExpenseSummary("2024-06", data)
        assertEquals(0L, result.totalAmount)
        assertEquals(0, result.items.size)
    }

    @Test
    fun recurringOnlyItemsAreIncluded() {
        val data =
            MonthlyMoney(
                month = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "i1",
                            name = "Internet",
                            amount = 5000L,
                            tags = listOf(MoneyTags.RECURRING),
                        ),
                    ),
            )
        val result = buildExpenseSummary("2024-06", data)
        assertEquals(5000L, result.totalAmount)
        assertEquals(1, result.items.size)
    }

    @Test
    fun allCarryOverReturnsZero() {
        val data =
            MonthlyMoney(
                month = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "i1",
                            name = "繰越A",
                            amount = 3000L,
                            tags = listOf(MoneyTags.CARRY_OVER),
                        ),
                        MoneyItem(
                            id = "i2",
                            name = "繰越B",
                            amount = 7000L,
                            tags = listOf(MoneyTags.CARRY_OVER),
                        ),
                    ),
            )
        val result = buildExpenseSummary("2024-06", data)
        assertEquals(0L, result.totalAmount)
        assertEquals(0, result.items.size)
    }
}
