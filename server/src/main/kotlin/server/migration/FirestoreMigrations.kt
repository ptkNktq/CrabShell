package server.migration

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import org.slf4j.LoggerFactory
import server.util.await

private const val MIGRATIONS_COLLECTION = "_migrations"
private const val MONEY_COLLECTION = "money"

// Firestore の WriteBatch 1 コミットあたりの最大オペレーション数。
// https://firebase.google.com/docs/firestore/manage-data/transactions#batched-writes
private const val FIRESTORE_BATCH_LIMIT = 500

private val logger = LoggerFactory.getLogger("server.migration.FirestoreMigrations")

/** money.month → yearMonth マイグレーションにおける 1 ドキュメントの扱い。 */
internal enum class MoneyMigrationAction {
    /** 対象外。新フィールドのみ保持済み、または両方未設定。 */
    SKIP,

    /** 新旧両方保持しているため、旧 `month` フィールドのみ削除する。 */
    DELETE_LEGACY,

    /** 旧 `month` フィールドのみ保持しているため、`yearMonth` を `doc.id` からセット & 旧 `month` を削除する。 */
    SET_NEW_AND_DELETE_LEGACY,
}

/** money ドキュメントの旧/新スキーマ保有状況から、必要なマイグレーション操作を判定する。 */
internal fun classifyMoneyMigration(
    hasLegacyMonth: Boolean,
    hasYearMonth: Boolean,
): MoneyMigrationAction =
    when {
        !hasLegacyMonth -> MoneyMigrationAction.SKIP
        hasYearMonth -> MoneyMigrationAction.DELETE_LEGACY
        else -> MoneyMigrationAction.SET_NEW_AND_DELETE_LEGACY
    }

/**
 * Firestore の一回きりマイグレーションをサーバー起動時に実行する。
 *
 * 完了フラグは [MIGRATIONS_COLLECTION] の各マイグレーション名ドキュメントに保存する。
 * 2 回目以降の起動では完了フラグを 1 回読むだけで O(1) で skip する。
 *
 * 並走防止: 完了フラグの書き込みには [com.google.cloud.firestore.DocumentReference.create] を使い、
 * Precondition.documentNotExists() 相当の CAS で保護する。複数インスタンスが同時起動した場合でも
 * 完了フラグ書き込みは 1 回に収まり、冪等なマイグレーション本体の並走は許容する。
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

        try {
            flagRef
                .create(
                    mapOf(
                        "completedAt" to FieldValue.serverTimestamp(),
                        "affected" to affected,
                    ),
                ).await()
            logger.info("Firestore migration {} complete: {} document(s) updated", name, affected)
        } catch (e: Exception) {
            // 別インスタンスが先にフラグを書き込んだケース（ALREADY_EXISTS）。本体は冪等なので問題なし。
            logger.info(
                "Firestore migration {} flag write skipped (likely completed by another instance): {}",
                name,
                e.message,
            )
        }
    }

    /**
     * money コレクションの旧 `month` フィールドを `yearMonth` にリネームする。
     *
     * `doc.id` が年月文字列と一致する不変条件を利用し、`yearMonth` は `doc.id` から設定する。
     * 更新は WriteBatch で [FIRESTORE_BATCH_LIMIT] 件ずつまとめてコミットし、N ドキュメントに対する
     * Firestore RTT を N 回から ⌈N / 500⌉ 回に削減する。
     */
    private suspend fun migrateMoneyMonthToYearMonth(): Int {
        val docs =
            firestore
                .collection(MONEY_COLLECTION)
                .get()
                .await()
                .documents
        return commitInBatches(docs) { doc ->
            val data = doc.data
            val action =
                classifyMoneyMigration(
                    hasLegacyMonth = data.containsKey("month"),
                    hasYearMonth = data.containsKey("yearMonth"),
                )
            when (action) {
                MoneyMigrationAction.SKIP -> null
                MoneyMigrationAction.DELETE_LEGACY -> mapOf("month" to FieldValue.delete())
                MoneyMigrationAction.SET_NEW_AND_DELETE_LEGACY ->
                    mapOf(
                        "yearMonth" to doc.id,
                        "month" to FieldValue.delete(),
                    )
            }
        }
    }

    /**
     * [docs] に対して [buildUpdate] が返す更新内容を [FIRESTORE_BATCH_LIMIT] 件ずつ WriteBatch でコミットする。
     * [buildUpdate] が null を返したドキュメントは更新対象外。戻り値は更新されたドキュメント数。
     */
    private suspend fun commitInBatches(
        docs: List<QueryDocumentSnapshot>,
        buildUpdate: (QueryDocumentSnapshot) -> Map<String, Any>?,
    ): Int {
        var batch = firestore.batch()
        var pending = 0
        var affected = 0
        for (doc in docs) {
            val update = buildUpdate(doc) ?: continue
            batch.update(doc.reference, update)
            pending++
            affected++
            if (pending >= FIRESTORE_BATCH_LIMIT) {
                batch.commit().await()
                batch = firestore.batch()
                pending = 0
            }
        }
        if (pending > 0) batch.commit().await()
        return affected
    }
}
