package server.money

import model.MoneyTags
import model.MonthlyMoneyStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyParsingTest {
    @Test
    fun parseItemsWithTags() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "note" to "Monthly",
                    "tags" to listOf(MoneyTags.RECURRING),
                    "payments" to
                        listOf(
                            mapOf("uid" to "u1", "amount" to 50000L),
                            mapOf("uid" to "u2", "amount" to 50000L),
                        ),
                ),
            )
        val items = parseItems(raw)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("item1", item.id)
        assertEquals("Rent", item.name)
        assertEquals(100000L, item.amount)
        assertEquals("Monthly", item.note)
        assertEquals(listOf(MoneyTags.RECURRING), item.tags)
        assertEquals(2, item.payments.size)
        assertEquals("u1", item.payments[0].uid)
        assertEquals(50000L, item.payments[0].amount)
    }

    @Test
    fun parseItemsLegacyRecurringTrueConvertedToTag() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "recurring" to true,
                    "payments" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = parseItems(raw)
        assertEquals(1, items.size)
        assertEquals(listOf(MoneyTags.RECURRING), items[0].tags)
    }

    @Test
    fun parseItemsLegacyRecurringFalseResultsInEmptyTags() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Groceries",
                    "amount" to 5000L,
                    "recurring" to false,
                    "payments" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = parseItems(raw)
        assertEquals(1, items.size)
        assertEquals(emptyList(), items[0].tags)
    }

    @Test
    fun parseItemsNoTagsNoRecurringResultsInEmptyTags() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Groceries",
                    "amount" to 5000L,
                    "payments" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = parseItems(raw)
        assertEquals(1, items.size)
        assertEquals(emptyList(), items[0].tags)
    }

    @Test
    fun parseItemsTagsFieldTakesPrecedenceOverRecurring() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "tags" to listOf(MoneyTags.RECURRING),
                    "recurring" to true,
                    "payments" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = parseItems(raw)
        assertEquals(1, items.size)
        assertEquals(listOf(MoneyTags.RECURRING), items[0].tags)
    }

    @Test
    fun parseItemsReturnsEmptyForNull() {
        assertEquals(emptyList(), parseItems(null))
    }

    @Test
    fun parsePaymentRecordsFromMapList() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf("uid" to "u1", "amount" to 3000L, "paidAt" to "2024-06-01"),
                mapOf("uid" to "u2", "amount" to 5000L, "paidAt" to "2024-06-02"),
            )
        val records = parsePaymentRecords(raw)
        assertEquals(2, records.size)
        assertEquals("u1", records[0].uid)
        assertEquals(3000L, records[0].amount)
        assertEquals("2024-06-01", records[0].paidAt)
    }

    @Test
    fun parsePaymentRecordsReturnsEmptyForNull() {
        assertEquals(emptyList(), parsePaymentRecords(null))
    }

    @Test
    fun parseStatusFromExplicitString() {
        for (status in MonthlyMoneyStatus.entries) {
            assertEquals(status, parseStatus(status.name, null))
        }
    }

    @Test
    fun parseStatusFallsBackToLegacyLockedTrue() {
        assertEquals(MonthlyMoneyStatus.FROZEN, parseStatus(null, true))
    }

    @Test
    fun parseStatusFallsBackToLegacyLockedFalse() {
        assertEquals(MonthlyMoneyStatus.PENDING, parseStatus(null, false))
    }

    @Test
    fun parseStatusDefaultsToPendingWhenAbsent() {
        assertEquals(MonthlyMoneyStatus.PENDING, parseStatus(null, null))
    }

    @Test
    fun parseStatusStringTakesPrecedenceOverLegacyLocked() {
        assertEquals(MonthlyMoneyStatus.CONFIRMED, parseStatus("CONFIRMED", true))
    }

    @Test
    fun parseStatusFallsBackToFrozenForUnknownStringWhenLegacyLocked() {
        assertEquals(MonthlyMoneyStatus.FROZEN, parseStatus("UNKNOWN_VALUE", true))
    }

    @Test
    fun parseStatusFallsBackToPendingForUnknownStringWhenLegacyUnlocked() {
        assertEquals(MonthlyMoneyStatus.PENDING, parseStatus("UNKNOWN_VALUE", false))
    }

    @Test
    fun parseStatusFallsBackToPendingForUnknownStringWhenLegacyAbsent() {
        assertEquals(MonthlyMoneyStatus.PENDING, parseStatus("UNKNOWN_VALUE", null))
    }

    @Test
    fun parseStatusTreatsBlankStringAsAbsent() {
        assertEquals(MonthlyMoneyStatus.PENDING, parseStatus("", null))
        assertEquals(MonthlyMoneyStatus.PENDING, parseStatus("   ", null))
        assertEquals(MonthlyMoneyStatus.FROZEN, parseStatus("", true))
    }

    @Test
    fun parseStatusTrimsSurroundingWhitespace() {
        assertEquals(MonthlyMoneyStatus.CONFIRMED, parseStatus(" CONFIRMED ", null))
        assertEquals(MonthlyMoneyStatus.FROZEN, parseStatus("\tFROZEN\n", null))
    }
}
