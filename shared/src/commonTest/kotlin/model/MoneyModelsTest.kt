package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyModelsTest {
    private val json = Json

    // ---------------------------------------------------------------------------------
    // ドメインモデルの round-trip
    // ---------------------------------------------------------------------------------

    @Test
    fun shareRoundTrip() {
        val share = Share(uid = "u1", amount = 5000L)
        val encoded = json.encodeToString(Share.serializer(), share)
        val decoded = json.decodeFromString(Share.serializer(), encoded)
        assertEquals(share, decoded)
    }

    @Test
    fun paymentRoundTrip() {
        val payment = Payment(id = "test-id-1", uid = "u1", amount = 3000L, paidAt = "2024-06-01")
        val encoded = json.encodeToString(Payment.serializer(), payment)
        val decoded = json.decodeFromString(Payment.serializer(), encoded)
        assertEquals(payment, decoded)
    }

    @Test
    fun moneyItemRoundTripWithDefaults() {
        val jsonStr = """{"id":"m1","name":"Rent","amount":100000}"""
        val decoded = json.decodeFromString(MoneyItem.serializer(), jsonStr)
        assertEquals("m1", decoded.id)
        assertEquals("", decoded.note)
        assertEquals(emptyList(), decoded.shares)
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
                shares = listOf(Share(uid = "u1", amount = 4000L), Share(uid = "u2", amount = 4000L)),
                tags = listOf(MoneyTags.RECURRING),
                dueDate = "2024-06-25",
            )
        val encoded = json.encodeToString(MoneyItem.serializer(), item)
        val decoded = json.decodeFromString(MoneyItem.serializer(), encoded)
        assertEquals(item, decoded)
        assertEquals("2024-06-25", decoded.dueDate)
    }

    @Test
    fun moneyItemDueDateDefaultsToNull() {
        val jsonStr = """{"id":"m1","name":"Rent","amount":100000}"""
        val decoded = json.decodeFromString(MoneyItem.serializer(), jsonStr)
        assertEquals(null, decoded.dueDate)
    }

    @Test
    fun monthlyMoneyNestedRoundTrip() {
        val monthly =
            MonthlyMoney(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItem(id = "i1", name = "Water", amount = 3000L),
                    ),
                payments =
                    listOf(
                        Payment(id = "test-id-2", uid = "u1", amount = 3000L, paidAt = "2024-06-15"),
                    ),
            )
        val encoded = json.encodeToString(MonthlyMoney.serializer(), monthly)
        val decoded = json.decodeFromString(MonthlyMoney.serializer(), encoded)
        assertEquals(monthly, decoded)
    }

    @Test
    fun monthlyMoneyStatusDefault() {
        val jsonStr = """{"yearMonth":"2024-07"}"""
        val decoded = json.decodeFromString(MonthlyMoney.serializer(), jsonStr)
        assertEquals(MonthlyMoneyStatus.PENDING, decoded.status)
    }

    @Test
    fun monthlyMoneyStatusRoundTrip() {
        for (status in MonthlyMoneyStatus.entries) {
            val monthly = MonthlyMoney(yearMonth = "2024-07", status = status)
            val encoded = json.encodeToString(MonthlyMoney.serializer(), monthly)
            val decoded = json.decodeFromString(MonthlyMoney.serializer(), encoded)
            assertEquals(status, decoded.status)
        }
    }

    // ---------------------------------------------------------------------------------
    // Request DTO の round-trip と API 契約検証
    // ---------------------------------------------------------------------------------

    @Test
    fun payRequestRoundTrip() {
        val request = PayRequest(amount = 3000L)
        val encoded = json.encodeToString(PayRequest.serializer(), request)
        val decoded = json.decodeFromString(PayRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun payRequestSerializesOnlyAmount() {
        // `/api/money/{ym}/pay` の API 契約:
        // - uid は principal から取得
        // - isRedemption は常に false（精算は /report/balances/redeem 経由のみ）
        // - paidAt はサーバー側で生成（クライアント時計依存・改ざんを排除）
        // 全てクライアントには露出しないことを保証する。
        val encoded = json.encodeToString(PayRequest.serializer(), PayRequest(amount = 1000L))
        assertEquals("""{"amount":1000}""", encoded)
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
                            shares = listOf(ShareSaveRequest(uid = "u1", amount = 80000L)),
                            tags = listOf(MoneyTags.RECURRING),
                        ),
                    ),
            )
        val encoded = json.encodeToString(MonthlyMoneySaveRequest.serializer(), request)
        val decoded = json.decodeFromString(MonthlyMoneySaveRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun monthlyMoneySaveRequestSerializesWithoutStatusAndPayments() {
        // PUT /money/{ym} の API 契約:
        // - status はリクエスト body に含まれない（PATCH /status 専用）
        // - payments も含まれない（入金は POST /pay、精算は POST /report/balances/redeem 経由のみ）
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
    // ドメインモデル → SaveRequest 変換
    // ---------------------------------------------------------------------------------

    @Test
    fun monthlyMoneyToSaveRequestPreservesItemsOnly() {
        // toSaveRequest は items のみマッピングする。payments は PUT 経路で
        // クライアントから送らない契約のため SaveRequest 側に存在しない。
        val source =
            MonthlyMoney(
                yearMonth = "2024-06",
                items =
                    listOf(
                        MoneyItem(
                            id = "i1",
                            name = "Rent",
                            amount = 80000L,
                            note = "June",
                            shares = listOf(Share(uid = "u1", amount = 80000L)),
                            tags = listOf(MoneyTags.RECURRING),
                        ),
                    ),
                payments =
                    listOf(
                        Payment(id = "test-id-3", uid = "u1", amount = 50000L, paidAt = "2024-06-01", note = "card"),
                    ),
                status = MonthlyMoneyStatus.CONFIRMED,
            )
        val request = source.toSaveRequest()
        assertEquals(1, request.items.size)
        assertEquals("i1", request.items[0].id)
        assertEquals("Rent", request.items[0].name)
        assertEquals("June", request.items[0].note)
        assertEquals(1, request.items[0].shares.size)
        assertEquals("u1", request.items[0].shares[0].uid)
    }

    @Test
    fun moneyItemToSaveRequestPreservesDueDate() {
        val item = MoneyItem(id = "i1", name = "Rent", amount = 80000L, dueDate = "2024-06-25")
        assertEquals("2024-06-25", item.toSaveRequest().dueDate)
    }

    // ---------------------------------------------------------------------------------
    // groupedByDueDate
    // ---------------------------------------------------------------------------------

    @Test
    fun groupedByDueDateSortsAscendingWithUnsetLast() {
        val items =
            listOf(
                MoneyItem(id = "i1", name = "B", amount = 1000L, dueDate = "2024-06-25"),
                MoneyItem(id = "i2", name = "A", amount = 2000L, dueDate = null),
                MoneyItem(id = "i3", name = "C", amount = 3000L, dueDate = "2024-06-10"),
            )
        val grouped = items.groupedByDueDate()
        assertEquals(listOf("2024-06-10", "2024-06-25", null), grouped.map { it.first })
        assertEquals(listOf("i3"), grouped[0].second.map { it.id })
        assertEquals(listOf("i1"), grouped[1].second.map { it.id })
        assertEquals(listOf("i2"), grouped[2].second.map { it.id })
    }

    @Test
    fun groupedByDueDateOmitsUnsetGroupWhenAllItemsHaveDueDate() {
        val items = listOf(MoneyItem(id = "i1", name = "A", amount = 1000L, dueDate = "2024-06-25"))
        val grouped = items.groupedByDueDate()
        assertEquals(listOf("2024-06-25"), grouped.map { it.first })
    }

    @Test
    fun groupedByDueDateReturnsEmptyForEmptyList() {
        assertEquals(emptyList(), emptyList<MoneyItem>().groupedByDueDate())
    }

    // ---------------------------------------------------------------------------------
    // MoneyDueDateNotificationSettings
    // ---------------------------------------------------------------------------------

    @Test
    fun moneyDueDateNotificationSettingsDefaults() {
        val settings = MoneyDueDateNotificationSettings()
        assertEquals(false, settings.enabled)
        assertEquals("", settings.webhookUrl)
        assertEquals(1, settings.daysBefore)
        assertEquals(23, settings.notifyHour)
        assertEquals("", settings.prefix)
    }

    @Test
    fun moneyDueDateNotificationSettingsRoundTrip() {
        val settings =
            MoneyDueDateNotificationSettings(
                enabled = true,
                webhookUrl = "https://discord.com/api/webhooks/x/y",
                daysBefore = 3,
                notifyHour = 9,
                prefix = "@everyone",
            )
        val encoded = json.encodeToString(MoneyDueDateNotificationSettings.serializer(), settings)
        val decoded = json.decodeFromString(MoneyDueDateNotificationSettings.serializer(), encoded)
        assertEquals(settings, decoded)
    }
}
