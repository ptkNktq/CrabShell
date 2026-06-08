package server.migration

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import org.slf4j.LoggerFactory
import server.util.await
import java.util.UUID

private const val MIGRATIONS_COLLECTION = "_migrations"
private const val MONEY_COLLECTION = "money"

// Firestore の WriteBatch 1 コミットあたりの最大オペレーション数。
// https://firebase.google.com/docs/firestore/manage-data/transactions#batched-writes
private const val FIRESTORE_BATCH_LIMIT = 500

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
    private val logger = LoggerFactory.getLogger(FirestoreMigrations::class.java)

    /** money.month → yearMonth マイグレーションにおける 1 ドキュメントの扱い。 */
    internal enum class MoneyMigrationAction {
        /** 対象外。新フィールドのみ保持済み、または両方未設定。 */
        SKIP,

        /** 新旧両方保持しているため、旧 `month` フィールドのみ削除する。 */
        DELETE_LEGACY,

        /** 旧 `month` フィールドのみ保持しているため、`yearMonth` を `doc.id` からセット & 旧 `month` を削除する。 */
        SET_NEW_AND_DELETE_LEGACY,
    }

    suspend fun runAll() {
        runIfNeeded("money-month-to-year-month") { migrateMoneyMonthToYearMonth() }
        runIfNeeded("money-rename-payments-and-shares") { migrateMoneyRenamePaymentsAndShares() }
        runIfNeeded("payments-fill-missing-ids") { migratePaymentsFillMissingIds() }
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
     * money コレクションのフィールド名を新スキーマに揃える。
     *
     * 旧スキーマ:
     * - トップレベル `paymentRecords`（振込ログ）
     * - 各 `items[].payments`（項目内の負担分担）
     *
     * 新スキーマ:
     * - トップレベル `payments`
     * - 各 `items[].shares`
     *
     * トップレベルとアイテム配列は独立に判定し、どちらかだけが旧スキーマの場合でも片側だけ
     * リネームする。具体的な状態判定とフィールド構築は [buildPaymentsAndSharesUpdate] に切り出して
     * ユニットテストの対象としている。
     */
    private suspend fun migrateMoneyRenamePaymentsAndShares(): Int {
        val docs =
            firestore
                .collection(MONEY_COLLECTION)
                .get()
                .await()
                .documents
        return commitInBatches(docs) { doc -> buildPaymentsAndSharesUpdate(doc.data) }
    }

    /**
     * money ドキュメントのトップレベル `paymentRecords` → `payments`、`items[].payments` → `items[].shares`
     * のフィールド rename に必要な Firestore update map を構築する純粋関数。
     *
     * 各フィールドの扱い:
     * - 旧フィールドのみ存在 → 旧の値を新フィールドへコピー、旧フィールドは削除（[FieldValue.delete]）
     * - 旧フィールドと新フィールドが同居 → 新フィールドは温存して旧フィールドだけ削除
     * - 新フィールドのみ、または両方とも未設定 → 何もしない
     *
     * 全フィールドが何もしない場合は `null` を返してドキュメントを更新対象から外す。
     *
     * 戻り値の `Map<String, Any>` は `Firestore.batch().update(ref, map)` がそのまま受け取る形式で、
     * 値には `FieldValue.delete()` 等の sentinel、`List<Map<...>>`、`Map<String, Any>` が混在する。
     */
    @Suppress("UNCHECKED_CAST")
    internal fun buildPaymentsAndSharesUpdate(data: Map<String, Any?>?): Map<String, Any>? {
        if (data == null) return null
        val update = mutableMapOf<String, Any>()

        // トップレベル paymentRecords → payments
        if (data.containsKey("paymentRecords")) {
            if (!data.containsKey("payments")) {
                update["payments"] = data["paymentRecords"] ?: emptyList<Any>()
            }
            update["paymentRecords"] = FieldValue.delete()
        }

        // items[].payments → items[].shares
        val items = data["items"] as? List<Map<String, Any?>>
        if (items != null) {
            var anyItemMigrated = false
            val newItems =
                items.map { item ->
                    if (!item.containsKey("payments")) return@map item
                    anyItemMigrated = true
                    val itemMap = item.toMutableMap()
                    if (!itemMap.containsKey("shares")) {
                        itemMap["shares"] = itemMap["payments"] ?: emptyList<Any>()
                    }
                    itemMap.remove("payments")
                    itemMap
                }
            if (anyItemMigrated) {
                update["items"] = newItems
            }
        }

        return if (update.isEmpty()) null else update
    }

    /**
     * money コレクションの `payments` 配列内で `id` フィールドが未設定（または空文字列）の
     * Payment に UUID を付与する。
     *
     * `id` は支払い取り消し機能で物理削除対象を一意に識別するために追加したフィールド。
     * 移行前の既存データには `id` が存在しないため、本マイグレーションで一括付与する。
     */
    private suspend fun migratePaymentsFillMissingIds(): Int {
        val docs =
            firestore
                .collection(MONEY_COLLECTION)
                .get()
                .await()
                .documents
        return commitInBatches(docs) { doc -> buildPaymentsFillMissingIdsUpdate(doc.data) }
    }

    /**
     * money ドキュメントの `payments` 配列内で `id` が未設定（または空文字列）の要素に UUID を付与する
     * 純粋関数。1 件でも更新が必要なら `mapOf("payments" to newPayments)` を返し、
     * 全要素が `id` 済みなら null を返してドキュメントを更新対象から外す。
     */
    @Suppress("UNCHECKED_CAST")
    internal fun buildPaymentsFillMissingIdsUpdate(data: Map<String, Any?>?): Map<String, Any>? {
        if (data == null) return null
        val payments = data["payments"] as? List<Map<String, Any?>> ?: return null
        var anyUpdated = false
        val newPayments =
            payments.map { payment ->
                val existingId = (payment["id"] as? String)?.takeIf { it.isNotEmpty() }
                if (existingId != null) {
                    payment
                } else {
                    anyUpdated = true
                    payment.toMutableMap().apply { put("id", UUID.randomUUID().toString()) }
                }
            }
        return if (anyUpdated) mapOf("payments" to newPayments) else null
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
