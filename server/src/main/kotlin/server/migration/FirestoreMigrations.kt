package server.migration

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import org.slf4j.LoggerFactory
import server.util.await

private const val MIGRATIONS_COLLECTION = "_migrations"
private const val MONEY_COLLECTION = "money"

private val logger = LoggerFactory.getLogger("server.migration.FirestoreMigrations")

/**
 * Firestore の一回きりマイグレーションをサーバー起動時に実行する。
 *
 * 完了フラグは [MIGRATIONS_COLLECTION] の各マイグレーション名ドキュメントに保存する。
 * 2 回目以降の起動では完了フラグを 1 回読むだけで O(1) で skip する。
 */
class FirestoreMigrations(
    private val firestore: Firestore,
) {
    suspend fun runAll() {
        runIfNeeded("money-month-to-year-month") { migrateMoneyMonthToYearMonth() }
    }

    private suspend fun runIfNeeded(
        name: String,
        block: suspend () -> Int,
    ) {
        val flagRef = firestore.collection(MIGRATIONS_COLLECTION).document(name)
        if (flagRef.get().await().exists()) return

        logger.info("Running Firestore migration: {}", name)
        val affected =
            runCatching { block() }
                .onFailure { logger.error("Firestore migration {} failed", name, it) }
                .getOrThrow()
        flagRef
            .set(
                mapOf(
                    "completedAt" to FieldValue.serverTimestamp(),
                    "affected" to affected,
                ),
            ).await()
        logger.info("Firestore migration {} complete: {} document(s) updated", name, affected)
    }

    /**
     * money コレクションの旧 `month` フィールドを `yearMonth` にリネームする。
     *
     * doc.id が年月文字列と一致する不変条件を利用し、`yearMonth` は `doc.id` から設定する。
     * 既に `yearMonth` が存在するドキュメントは旧 `month` フィールドのみ削除する。
     */
    private suspend fun migrateMoneyMonthToYearMonth(): Int {
        val docs =
            firestore
                .collection(MONEY_COLLECTION)
                .get()
                .await()
                .documents
        var migrated = 0
        for (doc in docs) {
            val data = doc.data
            val hasLegacy = data.containsKey("month")
            val hasNew = data.containsKey("yearMonth")
            when {
                !hasLegacy && hasNew -> continue
                hasNew -> {
                    doc.reference.update(mapOf("month" to FieldValue.delete())).await()
                    migrated++
                }
                hasLegacy -> {
                    doc.reference
                        .update(
                            mapOf(
                                "yearMonth" to doc.id,
                                "month" to FieldValue.delete(),
                            ),
                        ).await()
                    migrated++
                }
                else -> continue
            }
        }
        return migrated
    }
}
