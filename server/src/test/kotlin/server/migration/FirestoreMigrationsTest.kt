package server.migration

import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreMigrationsTest {
    @Test
    fun legacyOnlySetsNewAndDeletesLegacy() {
        assertEquals(
            MoneyMigrationAction.SET_NEW_AND_DELETE_LEGACY,
            classifyMoneyMigration(hasLegacyMonth = true, hasYearMonth = false),
        )
    }

    @Test
    fun bothFieldsDeletesLegacyOnly() {
        assertEquals(
            MoneyMigrationAction.DELETE_LEGACY,
            classifyMoneyMigration(hasLegacyMonth = true, hasYearMonth = true),
        )
    }

    @Test
    fun newOnlySkips() {
        assertEquals(
            MoneyMigrationAction.SKIP,
            classifyMoneyMigration(hasLegacyMonth = false, hasYearMonth = true),
        )
    }

    @Test
    fun neitherFieldSkips() {
        assertEquals(
            MoneyMigrationAction.SKIP,
            classifyMoneyMigration(hasLegacyMonth = false, hasYearMonth = false),
        )
    }
}
