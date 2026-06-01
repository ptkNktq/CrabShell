package server.migration

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirestoreMigrationsTest {
    // classifyMoneyMigration / buildPaymentsAndSharesUpdate は Firestore に触れない純粋関数だが、
    // メンバ関数なので FirestoreMigrations を instance 化する必要がある。
    // relaxed = true で未使用メソッド呼び出しが起きても no-op を返すダミー Firestore を用意する。
    private val firestoreMigrations = FirestoreMigrations(mockk<Firestore>(relaxed = true))

    // ===================================================================================
    // classifyMoneyMigration: money.month → yearMonth
    // ===================================================================================

    @Test
    fun legacyOnlySetsNewAndDeletesLegacy() {
        assertEquals(
            FirestoreMigrations.MoneyMigrationAction.SET_NEW_AND_DELETE_LEGACY,
            firestoreMigrations.classifyMoneyMigration(hasLegacyMonth = true, hasYearMonth = false),
        )
    }

    @Test
    fun bothFieldsDeletesLegacyOnly() {
        assertEquals(
            FirestoreMigrations.MoneyMigrationAction.DELETE_LEGACY,
            firestoreMigrations.classifyMoneyMigration(hasLegacyMonth = true, hasYearMonth = true),
        )
    }

    @Test
    fun newOnlySkips() {
        assertEquals(
            FirestoreMigrations.MoneyMigrationAction.SKIP,
            firestoreMigrations.classifyMoneyMigration(hasLegacyMonth = false, hasYearMonth = true),
        )
    }

    @Test
    fun neitherFieldSkips() {
        assertEquals(
            FirestoreMigrations.MoneyMigrationAction.SKIP,
            firestoreMigrations.classifyMoneyMigration(hasLegacyMonth = false, hasYearMonth = false),
        )
    }

    // ===================================================================================
    // buildPaymentsAndSharesUpdate: paymentRecords → payments / items[].payments → items[].shares
    // ===================================================================================

    @Test
    fun paymentsAndSharesReturnsNullForNullData() {
        assertNull(firestoreMigrations.buildPaymentsAndSharesUpdate(null))
    }

    @Test
    fun paymentsAndSharesReturnsNullWhenNewSchemaOnly() {
        val data =
            mapOf(
                "payments" to listOf(mapOf("uid" to "u1", "amount" to 1000L, "paidAt" to "2024-06-01")),
                "items" to
                    listOf(
                        mapOf("id" to "i1", "shares" to listOf(mapOf("uid" to "u1", "amount" to 500L))),
                    ),
            )
        assertNull(firestoreMigrations.buildPaymentsAndSharesUpdate(data))
    }

    @Test
    fun paymentsAndSharesRenamesTopLevelOnly() {
        val data =
            mapOf(
                "paymentRecords" to listOf(mapOf("uid" to "u1", "amount" to 1000L, "paidAt" to "2024-06-01")),
            )
        val update = firestoreMigrations.buildPaymentsAndSharesUpdate(data)
        assertEquals(2, update?.size)
        assertEquals(data["paymentRecords"], update?.get("payments"))
        assertEquals(FieldValue.delete(), update?.get("paymentRecords"))
    }

    @Test
    fun paymentsAndSharesRenamesItemsOnly() {
        val data =
            mapOf(
                "items" to
                    listOf(
                        mapOf(
                            "id" to "i1",
                            "payments" to listOf(mapOf("uid" to "u1", "amount" to 500L)),
                        ),
                    ),
            )
        val update = firestoreMigrations.buildPaymentsAndSharesUpdate(data)
        val newItems = update.expectItems()
        assertEquals(1, newItems.size)
        assertTrue(newItems[0].containsKey("shares"))
        assertEquals(false, newItems[0].containsKey("payments"))
        assertEquals(listOf(mapOf("uid" to "u1", "amount" to 500L)), newItems[0]["shares"])
    }

    @Test
    fun paymentsAndSharesRenamesBoth() {
        val data =
            mapOf(
                "paymentRecords" to listOf(mapOf("uid" to "u1", "amount" to 1000L, "paidAt" to "2024-06-01")),
                "items" to
                    listOf(
                        mapOf(
                            "id" to "i1",
                            "payments" to listOf(mapOf("uid" to "u1", "amount" to 500L)),
                        ),
                    ),
            )
        val update = firestoreMigrations.buildPaymentsAndSharesUpdate(data)
        // payments + paymentRecords削除 + items の合計 3 キー
        assertEquals(3, update?.size)
        assertEquals(data["paymentRecords"], update?.get("payments"))
        assertEquals(FieldValue.delete(), update?.get("paymentRecords"))
        assertTrue(update?.containsKey("items") == true)
    }

    @Test
    fun paymentsAndSharesPreservesNewWhenBothPresent() {
        // 旧 + 新が同居している場合、新フィールドは温存して旧フィールドだけ削除する
        val newPayments = listOf(mapOf("uid" to "u1", "amount" to 9999L, "paidAt" to "2024-06-02"))
        val legacyPaymentRecords = listOf(mapOf("uid" to "u1", "amount" to 1000L, "paidAt" to "2024-06-01"))
        val data =
            mapOf(
                "payments" to newPayments,
                "paymentRecords" to legacyPaymentRecords,
            )
        val update = firestoreMigrations.buildPaymentsAndSharesUpdate(data)
        assertEquals(1, update?.size)
        // 新フィールドは update に含めない（温存）
        assertEquals(false, update?.containsKey("payments"))
        assertEquals(FieldValue.delete(), update?.get("paymentRecords"))
    }

    @Test
    fun paymentsAndSharesPreservesItemSharesWhenBothPresent() {
        // items[].shares が既に存在する場合、items[].shares は温存して items[].payments だけ削除する
        val data =
            mapOf(
                "items" to
                    listOf(
                        mapOf(
                            "id" to "i1",
                            "shares" to listOf(mapOf("uid" to "u1", "amount" to 9999L)),
                            "payments" to listOf(mapOf("uid" to "u1", "amount" to 500L)),
                        ),
                    ),
            )
        val update = firestoreMigrations.buildPaymentsAndSharesUpdate(data)
        val newItems = update.expectItems()
        assertEquals(listOf(mapOf("uid" to "u1", "amount" to 9999L)), newItems[0]["shares"])
        assertEquals(false, newItems[0].containsKey("payments"))
    }

    @Test
    fun paymentsAndSharesSkipsItemsWithoutLegacyField() {
        // items[] が全て新フィールド `shares` だけを持つ場合、items は update に含めない
        val data =
            mapOf(
                "paymentRecords" to listOf(mapOf("uid" to "u1", "amount" to 1000L, "paidAt" to "2024-06-01")),
                "items" to
                    listOf(
                        mapOf(
                            "id" to "i1",
                            "shares" to listOf(mapOf("uid" to "u1", "amount" to 500L)),
                        ),
                    ),
            )
        val update = firestoreMigrations.buildPaymentsAndSharesUpdate(data)
        // トップレベルだけが対象、items は触らない
        assertEquals(2, update?.size)
        assertEquals(false, update?.containsKey("items"))
    }
}

/**
 * `update["items"]` を型付きで取り出すヘルパ。
 * Firestore 戻り値の `Any` 混在型を assertions 側で安全に展開する。
 */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>?.expectItems(): List<Map<String, Any?>> {
    val items = this?.get("items") ?: error("update must contain 'items' key")
    return items as? List<Map<String, Any?>> ?: error("'items' must be List<Map<String, Any?>>")
}
