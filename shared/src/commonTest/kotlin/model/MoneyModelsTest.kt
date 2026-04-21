package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyModelsTest {
    private val json = Json
    private val jsonWithDefaults = Json { encodeDefaults = true }

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
        assertEquals(emptyList(), decoded.tags)
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
                tags = listOf(MoneyTags.RECURRING),
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

    @Test
    fun moneyItemWithCarryOverTagRoundTrip() {
        val item =
            MoneyItem(
                id = "m3",
                name = "前月不足分",
                amount = 5000L,
                tags = listOf(MoneyTags.CARRY_OVER),
            )
        val encoded = json.encodeToString(MoneyItem.serializer(), item)
        val decoded = json.decodeFromString(MoneyItem.serializer(), encoded)
        assertEquals(item, decoded)
        assertEquals(listOf(MoneyTags.CARRY_OVER), decoded.tags)
    }

    @Test
    fun moneyItemWithMultipleTagsRoundTrip() {
        val item =
            MoneyItem(
                id = "m4",
                name = "毎月繰越",
                amount = 3000L,
                tags = listOf(MoneyTags.RECURRING, MoneyTags.CARRY_OVER),
            )
        val encoded = json.encodeToString(MoneyItem.serializer(), item)
        val decoded = json.decodeFromString(MoneyItem.serializer(), encoded)
        assertEquals(item, decoded)
        assertEquals(2, decoded.tags.size)
    }

    @Test
    fun monthlyMoneyStatusDefault() {
        val jsonStr = """{"month":"2024-07"}"""
        val decoded = json.decodeFromString(MonthlyMoney.serializer(), jsonStr)
        assertEquals(MonthlyMoneyStatus.PENDING, decoded.status)
    }

    @Test
    fun monthlyMoneyStatusRoundTrip() {
        for (status in MonthlyMoneyStatus.entries) {
            val monthly = MonthlyMoney(month = "2024-07", status = status)
            val encoded = json.encodeToString(MonthlyMoney.serializer(), monthly)
            val decoded = json.decodeFromString(MonthlyMoney.serializer(), encoded)
            assertEquals(status, decoded.status)
        }
    }

    @Test
    fun monthlyMoneyStatusWireValueMatchesJsonRepresentation() {
        for (status in MonthlyMoneyStatus.entries) {
            val monthly = MonthlyMoney(month = "2024-07", status = status)
            val encoded = jsonWithDefaults.encodeToString(MonthlyMoney.serializer(), monthly)
            assertEquals(true, encoded.contains("\"status\":\"${status.wireValue}\""))
        }
    }

    @Test
    fun monthlyMoneyStatusFromWireValue() {
        for (status in MonthlyMoneyStatus.entries) {
            assertEquals(status, MonthlyMoneyStatus.fromWireValue(status.wireValue))
        }
        assertEquals(null, MonthlyMoneyStatus.fromWireValue("UNKNOWN"))
    }
}
