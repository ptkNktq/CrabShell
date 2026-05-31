package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyModelsTest {
    private val json = Json

    // ---------------------------------------------------------------------------------
    // Response DTO の round-trip
    // ---------------------------------------------------------------------------------

    @Test
    fun paymentResponseRoundTrip() {
        val payment = PaymentResponse(uid = "u1", amount = 5000L)
        val encoded = json.encodeToString(PaymentResponse.serializer(), payment)
        val decoded = json.decodeFromString(PaymentResponse.serializer(), encoded)
        assertEquals(payment, decoded)
    }

    @Test
    fun paymentRecordResponseRoundTrip() {
        val record = PaymentRecordResponse(uid = "u1", amount = 3000L, paidAt = "2024-06-01")
        val encoded = json.encodeToString(PaymentRecordResponse.serializer(), record)
        val decoded = json.decodeFromString(PaymentRecordResponse.serializer(), encoded)
        assertEquals(record, decoded)
    }

    @Test
    fun moneyItemResponseRoundTripWithDefaults() {
        val jsonStr = """{"id":"m1","name":"Rent","amount":100000}"""
        val decoded = json.decodeFromString(MoneyItemResponse.serializer(), jsonStr)
        assertEquals("m1", decoded.id)
        assertEquals("", decoded.note)
        assertEquals(emptyList(), decoded.payments)
        assertEquals(emptyList(), decoded.tags)
    }

    @Test
    fun moneyItemResponseFullRoundTrip() {
        val item =
            MoneyItemResponse(
                id = "m2",
                name = "Electric",
                amount = 8000L,
                note = "June",
                payments = listOf(PaymentResponse(uid = "u1", amount = 4000L), PaymentResponse(uid = "u2", amount = 4000L)),
                tags = listOf(MoneyTags.RECURRING),
            )
        val encoded = json.encodeToString(MoneyItemResponse.serializer(), item)
        val decoded = json.decodeFromString(MoneyItemResponse.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun monthlyMoneyResponseNestedRoundTrip() {
        val monthly =
            MonthlyMoneyResponse(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItemResponse(id = "i1", name = "Water", amount = 3000L),
                    ),
                paymentRecords =
                    listOf(
                        PaymentRecordResponse(uid = "u1", amount = 3000L, paidAt = "2024-06-15"),
                    ),
            )
        val encoded = json.encodeToString(MonthlyMoneyResponse.serializer(), monthly)
        val decoded = json.decodeFromString(MonthlyMoneyResponse.serializer(), encoded)
        assertEquals(monthly, decoded)
    }

    @Test
    fun monthlyMoneyResponseStatusDefault() {
        val jsonStr = """{"yearMonth":"2024-07"}"""
        val decoded = json.decodeFromString(MonthlyMoneyResponse.serializer(), jsonStr)
        assertEquals(MonthlyMoneyStatus.PENDING, decoded.status)
    }

    @Test
    fun monthlyMoneyResponseStatusRoundTrip() {
        for (status in MonthlyMoneyStatus.entries) {
            val monthly = MonthlyMoneyResponse(yearMonth = "2024-07", status = status)
            val encoded = json.encodeToString(MonthlyMoneyResponse.serializer(), monthly)
            val decoded = json.decodeFromString(MonthlyMoneyResponse.serializer(), encoded)
            assertEquals(status, decoded.status)
        }
    }

    // ---------------------------------------------------------------------------------
    // Request DTO の round-trip と API 契約検証
    // ---------------------------------------------------------------------------------

    @Test
    fun payRequestRoundTrip() {
        val request = PayRequest(amount = 3000L, paidAt = "2024-06-01")
        val encoded = json.encodeToString(PayRequest.serializer(), request)
        val decoded = json.decodeFromString(PayRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun payRequestSerializesOnlyAmountAndPaidAt() {
        // クライアントが触ってはいけない uid / isRedemption がリクエスト DTO に
        // 露出していないことを保証する。`/api/money/{ym}/pay` の API 契約。
        val encoded = json.encodeToString(PayRequest.serializer(), PayRequest(amount = 1000L, paidAt = "2024-06-01"))
        assertEquals("""{"amount":1000,"paidAt":"2024-06-01"}""", encoded)
    }

    @Test
    fun monthlyMoneySaveRequestRoundTrip() {
        val request =
            MonthlyMoneySaveRequest(
                items =
                    listOf(
                        MoneyItemSaveRequest(
                            id = "i1",
                            name = "Rent",
                            amount = 80000L,
                            payments = listOf(PaymentSaveRequest(uid = "u1", amount = 80000L)),
                            tags = listOf(MoneyTags.RECURRING),
                        ),
                    ),
                paymentRecords =
                    listOf(
                        PaymentRecordSaveRequest(uid = "u1", amount = 50000L, paidAt = "2024-06-01"),
                    ),
            )
        val encoded = json.encodeToString(MonthlyMoneySaveRequest.serializer(), request)
        val decoded = json.decodeFromString(MonthlyMoneySaveRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun monthlyMoneySaveRequestSerializesWithoutStatus() {
        // PUT /money/{ym} の API 契約: status はリクエスト body に含まれない（PATCH /status 専用）
        val encoded =
            json.encodeToString(
                MonthlyMoneySaveRequest.serializer(),
                MonthlyMoneySaveRequest(),
            )
        assertEquals("""{}""", encoded)
    }

    @Test
    fun monthlyMoneyStatusUpdateRequestRoundTrip() {
        for (status in MonthlyMoneyStatus.entries) {
            val request = MonthlyMoneyStatusUpdateRequest(status = status)
            val encoded = json.encodeToString(MonthlyMoneyStatusUpdateRequest.serializer(), request)
            val decoded = json.decodeFromString(MonthlyMoneyStatusUpdateRequest.serializer(), encoded)
            assertEquals(status, decoded.status)
        }
    }

    // ---------------------------------------------------------------------------------
    // Response → SaveRequest 変換
    // ---------------------------------------------------------------------------------

    @Test
    fun monthlyMoneyResponseToSaveRequestPreservesNestedFields() {
        val response =
            MonthlyMoneyResponse(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItemResponse(
                            id = "i1",
                            name = "Rent",
                            amount = 80000L,
                            note = "June",
                            payments = listOf(PaymentResponse(uid = "u1", amount = 80000L)),
                            tags = listOf(MoneyTags.RECURRING),
                        ),
                    ),
                paymentRecords =
                    listOf(
                        PaymentRecordResponse(uid = "u1", amount = 50000L, paidAt = "2024-06-01", note = "card"),
                    ),
                status = MonthlyMoneyStatus.CONFIRMED,
            )
        val request = response.toSaveRequest()
        assertEquals(1, request.items.size)
        assertEquals("i1", request.items[0].id)
        assertEquals("Rent", request.items[0].name)
        assertEquals("June", request.items[0].note)
        assertEquals(1, request.items[0].payments.size)
        assertEquals("u1", request.items[0].payments[0].uid)
        assertEquals(1, request.paymentRecords.size)
        assertEquals("card", request.paymentRecords[0].note)
    }
}
