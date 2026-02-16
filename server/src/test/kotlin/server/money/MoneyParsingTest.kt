package server.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyParsingTest {
    @Test
    fun parseItemsFromMapList() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "note" to "Monthly",
                    "recurring" to true,
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
        assertEquals(true, item.recurring)
        assertEquals(2, item.payments.size)
        assertEquals("u1", item.payments[0].uid)
        assertEquals(50000L, item.payments[0].amount)
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
}
