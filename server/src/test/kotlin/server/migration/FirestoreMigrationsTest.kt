package server.migration

import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreMigrationsTest {
    // classifyMoneyMigration は Firestore に触れない純粋関数だが、メンバ関数なので
    // FirestoreMigrations を instance 化する必要がある。relaxed = true で未使用メソッド呼び出しが
    // 起きても no-op を返すダミー Firestore を用意する。
    private val firestoreMigrations = FirestoreMigrations(mockk<Firestore>(relaxed = true))

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
}
