package server.money

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import model.MoneyItem
import model.MoneyTags
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.Payment
import model.PaymentRecord
import org.slf4j.LoggerFactory
import server.cache.Cacheable
import server.util.await
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val MONEY_COLLECTION = "money"
private val logger = LoggerFactory.getLogger("server.money.FirestoreMoneyRepository")

class FirestoreMoneyRepository(
    private val firestore: Firestore,
) : MoneyRepository,
    Cacheable {
    override val cacheName: String = "money"

    override fun clearCache() {
        cache.clear()
        allMonthsLoaded.set(false)
    }

    private val cache = ConcurrentHashMap<String, MonthlyMoney>()
    private val allMonthsLoaded = AtomicBoolean(false)

    override suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoney? {
        cache[yearMonth]?.let { return it }

        val doc =
            firestore
                .collection(MONEY_COLLECTION)
                .document(yearMonth)
                .get()
                .await()
        if (!doc.exists()) return null

        val data = parseMonthlyMoney(yearMonth, doc)
        cache[yearMonth] = data
        return data
    }

    /**
     * 月次お金データを Firestore に保存する。
     *
     * `set(Map)` は `SetOptions.merge()` を指定しないためドキュメント全体を置換する。
     * これにより、旧スキーマの `locked: Boolean` や `month` フィールドを持つドキュメントを保存した際に
     * 自動で削除され、legacy フィールドが残留しない。
     * 将来この呼び出しを `merge` に変える場合は、`"locked" to FieldValue.delete()` などを明示的に
     * 含めて legacy フィールドの除去を維持すること。
     */
    override suspend fun saveMonthlyMoney(
        yearMonth: String,
        data: MonthlyMoney,
    ) {
        val items =
            data.items.map { item ->
                mapOf(
                    "id" to item.id,
                    "name" to item.name,
                    "amount" to item.amount,
                    "note" to item.note,
                    "tags" to item.tags,
                    "payments" to
                        item.payments.map { p ->
                            mapOf("uid" to p.uid, "amount" to p.amount)
                        },
                )
            }

        val records =
            data.paymentRecords.map { r ->
                mapOf("uid" to r.uid, "amount" to r.amount, "paidAt" to r.paidAt, "note" to r.note, "isRedemption" to r.isRedemption)
            }

        firestore
            .collection(MONEY_COLLECTION)
            .document(yearMonth)
            .set(mapOf("yearMonth" to yearMonth, "items" to items, "paymentRecords" to records, "status" to data.status.name))
            .await()

        cache[yearMonth] = data
    }

    override suspend fun importItemsByTag(
        targetYearMonth: String,
        tag: String,
    ): MonthlyMoney {
        val previousYearMonth = YearMonth.parse(targetYearMonth).minusMonths(1).toString()
        val prevData = getMonthlyMoney(previousYearMonth)
        val taggedItems =
            (prevData?.items ?: emptyList())
                .filter { tag in it.tags }
                .map { item -> item.copy(id = UUID.randomUUID().toString()) }

        val existing = getMonthlyMoney(targetYearMonth) ?: MonthlyMoney(yearMonth = targetYearMonth)
        val merged = existing.copy(items = existing.items + taggedItems)
        saveMonthlyMoney(targetYearMonth, merged)
        return merged
    }

    override suspend fun getAllMonths(): List<MonthlyMoney> {
        if (allMonthsLoaded.get()) {
            return cache.values.toList()
        }

        val docs =
            firestore
                .collection(MONEY_COLLECTION)
                .get()
                .await()
                .documents
        val months = docs.map { doc -> parseMonthlyMoney(doc.id, doc) }
        months.forEach { cache[it.yearMonth] = it }
        allMonthsLoaded.set(true)
        return months
    }

    private fun parseMonthlyMoney(
        yearMonth: String,
        doc: DocumentSnapshot,
    ): MonthlyMoney {
        val items = parseItems(doc.get("items"))
        val records = parsePaymentRecords(doc.get("paymentRecords"))
        val status = parseStatus(doc.getString("status"), doc.getBoolean("locked"))
        return MonthlyMoney(yearMonth = yearMonth, items = items, paymentRecords = records, status = status)
    }
}

/**
 * Firestore の生フィールドから MonthlyMoneyStatus を復元する。
 *
 * - 新形式: `status: "FROZEN"` 等の文字列。enum の name で復元する。
 * - 未知の文字列: WARN ログを出した上で旧形式にフォールバックする。
 * - 旧形式 (`locked: Boolean`): `locked=true → FROZEN` に変換する。
 *
 * それ以外（status 未設定 + locked=false または未設定）は [MonthlyMoneyStatus.PENDING] を
 * デフォルトとして返す。旧運用では `locked=false` は単に「編集可能」を意味し、新 3 状態の
 * 「確定済みか未確定か」という情報は存在しなかったため、安全側に倒して PENDING とする。
 * 既に運用上確定していた月は、マイグレーション後に admin が手動で CONFIRMED に切り替える
 * ことを想定する。
 */
internal fun parseStatus(
    statusRaw: String?,
    legacyLocked: Boolean?,
): MonthlyMoneyStatus {
    val normalized = statusRaw?.trim()?.takeIf { it.isNotEmpty() }
    if (normalized != null) {
        val parsed = runCatching { MonthlyMoneyStatus.valueOf(normalized) }.getOrNull()
        if (parsed != null) return parsed
        logger.warn("Unknown MonthlyMoneyStatus value: {} — falling back to legacy locked", normalized)
    }
    return if (legacyLocked == true) MonthlyMoneyStatus.FROZEN else MonthlyMoneyStatus.PENDING
}

/** Map リストから MoneyItem リストをパースする（テスト用に internal） */
@Suppress("UNCHECKED_CAST")
internal fun parseItems(raw: Any?): List<MoneyItem> {
    val itemsRaw = raw as? List<Map<String, Any?>> ?: return emptyList()
    return itemsRaw.map { entry ->
        val paymentsRaw = entry["payments"] as? List<Map<String, Any?>> ?: emptyList()
        // tags フィールドを読み取り。レガシーデータ対応: recurring=true → tags=["毎月"]
        val tags =
            (entry["tags"] as? List<String>)
                ?: if (entry["recurring"] as? Boolean == true) listOf(MoneyTags.RECURRING) else emptyList()
        MoneyItem(
            id = entry["id"] as String,
            name = entry["name"] as String,
            amount = (entry["amount"] as Number).toLong(),
            note = entry["note"] as? String ?: "",
            tags = tags,
            payments =
                paymentsRaw.map { p ->
                    Payment(
                        uid = p["uid"] as String,
                        amount = (p["amount"] as Number).toLong(),
                    )
                },
        )
    }
}

/** Map リストから PaymentRecord リストをパースする（テスト用に internal） */
@Suppress("UNCHECKED_CAST")
internal fun parsePaymentRecords(raw: Any?): List<PaymentRecord> {
    val recordsRaw = raw as? List<Map<String, Any?>> ?: return emptyList()
    return recordsRaw.map { r ->
        PaymentRecord(
            uid = r["uid"] as String,
            amount = (r["amount"] as Number).toLong(),
            paidAt = r["paidAt"] as String,
            note = r["note"] as? String ?: "",
            isRedemption = r["isRedemption"] as? Boolean ?: false,
        )
    }
}
