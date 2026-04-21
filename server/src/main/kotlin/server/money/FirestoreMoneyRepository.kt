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

    override suspend fun getMonthlyMoney(month: String): MonthlyMoney? {
        cache[month]?.let { return it }

        val doc =
            firestore
                .collection(MONEY_COLLECTION)
                .document(month)
                .get()
                .await()
        if (!doc.exists()) return null

        val data = parseMonthlyMoney(month, doc)
        cache[month] = data
        return data
    }

    override suspend fun saveMonthlyMoney(
        month: String,
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
            .document(month)
            .set(mapOf("month" to month, "items" to items, "paymentRecords" to records, "status" to data.status.name))
            .await()

        cache[month] = data
    }

    override suspend fun importItemsByTag(
        targetMonth: String,
        tag: String,
    ): MonthlyMoney {
        val previousMonth = YearMonth.parse(targetMonth).minusMonths(1).toString()
        val prevData = getMonthlyMoney(previousMonth)
        val taggedItems =
            (prevData?.items ?: emptyList())
                .filter { tag in it.tags }
                .map { item -> item.copy(id = UUID.randomUUID().toString()) }

        val existing = getMonthlyMoney(targetMonth) ?: MonthlyMoney(month = targetMonth)
        val merged = existing.copy(items = existing.items + taggedItems)
        saveMonthlyMoney(targetMonth, merged)
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
        months.forEach { cache[it.month] = it }
        allMonthsLoaded.set(true)
        return months
    }

    private fun parseMonthlyMoney(
        month: String,
        doc: DocumentSnapshot,
    ): MonthlyMoney {
        val items = parseItems(doc.get("items"))
        val records = parsePaymentRecords(doc.get("paymentRecords"))
        val status = parseStatus(doc.getString("status"), doc.getBoolean("locked"))
        return MonthlyMoney(month = month, items = items, paymentRecords = records, status = status)
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
    if (statusRaw != null) {
        val parsed = runCatching { MonthlyMoneyStatus.valueOf(statusRaw) }.getOrNull()
        if (parsed != null) return parsed
        logger.warn("Unknown MonthlyMoneyStatus value: {} — falling back to legacy locked", statusRaw)
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
