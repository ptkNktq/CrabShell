package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyModelsTest {
    private val json = Json

    @Test
    fun paymentRoundTrip() {
        val payment = Payment(uid = "u1", amount = 5000L)
        val encoded = json.encodeToString(Payment.serializer(), payment)
        val decoded = json.decodeFromString(Payment.serializer(), encoded)
        assertEquals(payment, decoded)
    }

    @Test
    fun paymentRecordRoundTrip() {
        val record = PaymentRecord(uid = "u1", amount = 3000L, paidAt = "2024-06-01")
        val encoded = json.encodeToString(PaymentRecord.serializer(), record)
        val decoded = json.decodeFromString(PaymentRecord.serializer(), encoded)
        assertEquals(record, decoded)
    }

    @Test
    fun moneyItemRoundTripWithDefaults() {
        val jsonStr = """{"id":"m1","name":"Rent","amount":100000}"""
        val decoded = json.decodeFromString(MoneyItem.serializer(), jsonStr)
        assertEquals("m1", decoded.id)
        assertEquals("", decoded.note)
        assertEquals(emptyList(), decoded.payments)
        assertEquals(false, decoded.recurring)
    }

    @Test
    fun moneyItemFullRoundTrip() {
        val item =
            MoneyItem(
                id = "m2",
                name = "Electric",
                amount = 8000L,
                note = "June",
                payments = listOf(Payment(uid = "u1", amount = 4000L), Payment(uid = "u2", amount = 4000L)),
                recurring = true,
            )
        val encoded = json.encodeToString(MoneyItem.serializer(), item)
        val decoded = json.decodeFromString(MoneyItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun monthlyMoneyNestedRoundTrip() {
        val monthly =
            MonthlyMoney(
                month = "2024-06",
                items =
                    listOf(
                        MoneyItem(id = "i1", name = "Water", amount = 3000L),
                    ),
                paymentRecords =
                    listOf(
                        PaymentRecord(uid = "u1", amount = 3000L, paidAt = "2024-06-15"),
                    ),
            )
        val encoded = json.encodeToString(MonthlyMoney.serializer(), monthly)
        val decoded = json.decodeFromString(MonthlyMoney.serializer(), encoded)
        assertEquals(monthly, decoded)
    }
}
