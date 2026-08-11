package server.money

import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import model.MoneyItem
import model.MoneyTags
import model.MonthlyMoneyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyParsingTest {
    // parse 関数群は Firestore に触れない純粋関数だが、メンバ関数なので
    // FirestoreMoneyRepository を instance 化する必要がある。relaxed = true で未使用メソッド呼び出しが
    // 起きても no-op を返すダミー Firestore を用意する。
    private val repository = FirestoreMoneyRepository(mockk<Firestore>(relaxed = true))

    @Test
    fun parseItemsWithTagsAndShares() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "note" to "Monthly",
                    "tags" to listOf(MoneyTags.RECURRING),
                    "shares" to
                        listOf(
                            mapOf("uid" to "u1", "amount" to 50000L),
                            mapOf("uid" to "u2", "amount" to 50000L),
                        ),
                ),
            )
        val items = repository.parseItems(raw)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("item1", item.id)
        assertEquals("Rent", item.name)
        assertEquals(100000L, item.amount)
        assertEquals("Monthly", item.note)
        assertEquals(listOf(MoneyTags.RECURRING), item.tags)
        assertEquals(2, item.shares.size)
        assertEquals("u1", item.shares[0].uid)
        assertEquals(50000L, item.shares[0].amount)
    }

    @Test
    fun parseItemsReadsDueDate() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "dueDate" to "2024-06-25",
                    "shares" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = repository.parseItems(raw)
        assertEquals("2024-06-25", items[0].dueDate)
    }

    @Test
    fun parseItemsDueDateDefaultsToNullWhenAbsent() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "shares" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = repository.parseItems(raw)
        assertEquals(null, items[0].dueDate)
    }

    @Test
    fun parseItemsLegacyPaymentsFieldFallbacksToShares() {
        // 旧スキーマ: items[].payments → 新スキーマ items[].shares への過渡期互換
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "id" to "item1",
                    "name" to "Rent",
                    "amount" to 100000L,
                    "payments" to
                        listOf(
                            mapOf("uid" to "u1", "amount" to 50000L),
                        ),
                ),
            )
        val items = repository.parseItems(raw)
        assertEquals(1, items.size)
        assertEquals(1, items[0].shares.size)
        assertEquals("u1", items[0].shares[0].uid)
        assertEquals(50000L, items[0].shares[0].amount)
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
                    "shares" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = repository.parseItems(raw)
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
                    "shares" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = repository.parseItems(raw)
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
                    "shares" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = repository.parseItems(raw)
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
                    "shares" to emptyList<Map<String, Any?>>(),
                ),
            )
        val items = repository.parseItems(raw)
        assertEquals(1, items.size)
        assertEquals(listOf(MoneyTags.RECURRING), items[0].tags)
    }

    @Test
    fun parseItemsReturnsEmptyForNull() {
        assertEquals(emptyList(), repository.parseItems(null))
    }

    @Test
    fun parsePaymentsFromMapList() {
        val raw: List<Map<String, Any?>> =
            listOf(
                mapOf("uid" to "u1", "amount" to 3000L, "paidAt" to "2024-06-01"),
                mapOf("uid" to "u2", "amount" to 5000L, "paidAt" to "2024-06-02"),
            )
        val payments = repository.parsePayments(raw)
        assertEquals(2, payments.size)
        assertEquals("u1", payments[0].uid)
        assertEquals(3000L, payments[0].amount)
        assertEquals("2024-06-01", payments[0].paidAt)
    }

    @Test
    fun parsePaymentsReturnsEmptyForNull() {
        assertEquals(emptyList(), repository.parsePayments(null))
    }

    @Test
    fun parseStatusFromExplicitString() {
        for (status in MonthlyMoneyStatus.entries) {
            assertEquals(status, repository.parseStatus(status.name, null))
        }
    }

    @Test
    fun parseStatusFallsBackToLegacyLockedTrue() {
        assertEquals(MonthlyMoneyStatus.FROZEN, repository.parseStatus(null, true))
    }

    @Test
    fun parseStatusFallsBackToLegacyLockedFalse() {
        assertEquals(MonthlyMoneyStatus.PENDING, repository.parseStatus(null, false))
    }

    @Test
    fun parseStatusDefaultsToPendingWhenAbsent() {
        assertEquals(MonthlyMoneyStatus.PENDING, repository.parseStatus(null, null))
    }

    @Test
    fun parseStatusStringTakesPrecedenceOverLegacyLocked() {
        assertEquals(MonthlyMoneyStatus.CONFIRMED, repository.parseStatus("CONFIRMED", true))
    }

    @Test
    fun parseStatusFallsBackToFrozenForUnknownStringWhenLegacyLocked() {
        assertEquals(MonthlyMoneyStatus.FROZEN, repository.parseStatus("UNKNOWN_VALUE", true))
    }

    @Test
    fun parseStatusFallsBackToPendingForUnknownStringWhenLegacyUnlocked() {
        assertEquals(MonthlyMoneyStatus.PENDING, repository.parseStatus("UNKNOWN_VALUE", false))
    }

    @Test
    fun parseStatusFallsBackToPendingForUnknownStringWhenLegacyAbsent() {
        assertEquals(MonthlyMoneyStatus.PENDING, repository.parseStatus("UNKNOWN_VALUE", null))
    }

    @Test
    fun parseStatusTreatsBlankStringAsAbsent() {
        assertEquals(MonthlyMoneyStatus.PENDING, repository.parseStatus("", null))
        assertEquals(MonthlyMoneyStatus.PENDING, repository.parseStatus("   ", null))
        assertEquals(MonthlyMoneyStatus.FROZEN, repository.parseStatus("", true))
    }

    @Test
    fun parseStatusTrimsSurroundingWhitespace() {
        assertEquals(MonthlyMoneyStatus.CONFIRMED, repository.parseStatus(" CONFIRMED ", null))
        assertEquals(MonthlyMoneyStatus.FROZEN, repository.parseStatus("\tFROZEN\n", null))
    }

    // ---------------------------------------------------------------------------------
    // filterTaggedItemsForImport
    // ---------------------------------------------------------------------------------

    @Test
    fun filterTaggedItemsForImportRebasesDueDateToTargetMonth() {
        // 前月の dueDate をそのまま複製すると、期日リマインダーが二度と一致せず
        // 永久に飛ばなくなるため、対象月の同じ日に付け替えられることを保証する回帰テスト。
        val items = listOf(MoneyItem(id = "i1", name = "家賃", amount = 80000L, tags = listOf(MoneyTags.RECURRING), dueDate = "2024-06-25"))
        val result = repository.filterTaggedItemsForImport(items, MoneyTags.RECURRING, "2024-07")
        assertEquals(1, result.size)
        assertEquals("2024-07-25", result[0].dueDate)
    }

    @Test
    fun filterTaggedItemsForImportNullsDueDateWhenTargetMonthHasNoSuchDay() {
        // 1/31 の項目を 2月にインポートすると 2月に31日は存在しないため、クランプせず null にする。
        val items = listOf(MoneyItem(id = "i1", name = "家賃", amount = 80000L, tags = listOf(MoneyTags.RECURRING), dueDate = "2024-01-31"))
        val result = repository.filterTaggedItemsForImport(items, MoneyTags.RECURRING, "2024-02")
        assertNull(result[0].dueDate)
    }

    @Test
    fun filterTaggedItemsForImportKeepsNullDueDateAsNull() {
        val items = listOf(MoneyItem(id = "i1", name = "家賃", amount = 80000L, tags = listOf(MoneyTags.RECURRING), dueDate = null))
        val result = repository.filterTaggedItemsForImport(items, MoneyTags.RECURRING, "2024-07")
        assertNull(result[0].dueDate)
    }

    @Test
    fun filterTaggedItemsForImportExcludesUntaggedItems() {
        val items =
            listOf(
                MoneyItem(id = "i1", name = "家賃", amount = 80000L, tags = listOf(MoneyTags.RECURRING)),
                MoneyItem(id = "i2", name = "外食", amount = 3000L, tags = emptyList()),
            )
        val result = repository.filterTaggedItemsForImport(items, MoneyTags.RECURRING, "2024-07")
        assertEquals(listOf("i1"), result.map { it.id })
    }

    @Test
    fun filterTaggedItemsForImportPreservesOtherFields() {
        val items =
            listOf(
                MoneyItem(
                    id = "i1",
                    name = "家賃",
                    amount = 80000L,
                    note = "毎月25日払い",
                    tags = listOf(MoneyTags.RECURRING),
                    dueDate = "2024-06-25",
                ),
            )
        val result = repository.filterTaggedItemsForImport(items, MoneyTags.RECURRING, "2024-07")
        assertEquals("家賃", result[0].name)
        assertEquals(80000L, result[0].amount)
        assertEquals("毎月25日払い", result[0].note)
        assertEquals(listOf(MoneyTags.RECURRING), result[0].tags)
    }

    // ---------------------------------------------------------------------------------
    // rebaseDueDateToTargetMonth
    // ---------------------------------------------------------------------------------

    @Test
    fun rebaseDueDateToTargetMonthKeepsSameDay() {
        assertEquals("2024-07-25", repository.rebaseDueDateToTargetMonth("2024-06-25", "2024-07"))
    }

    @Test
    fun rebaseDueDateToTargetMonthReturnsNullWhenDayDoesNotExistInTargetMonth() {
        // 1/31 → 2月（うるう年でも29日まで）
        assertNull(repository.rebaseDueDateToTargetMonth("2024-01-31", "2024-02"))
    }

    @Test
    fun rebaseDueDateToTargetMonthHandlesLeapYearFebruary29() {
        assertEquals("2024-02-29", repository.rebaseDueDateToTargetMonth("2024-01-29", "2024-02"))
        assertNull(repository.rebaseDueDateToTargetMonth("2024-01-30", "2025-02"))
    }

    @Test
    fun rebaseDueDateToTargetMonthReturnsNullForMalformedInput() {
        assertNull(repository.rebaseDueDateToTargetMonth("not-a-date", "2024-07"))
        assertNull(repository.rebaseDueDateToTargetMonth("2024-06-25", "not-a-yearmonth"))
    }
}
