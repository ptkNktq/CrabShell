package server.money

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import model.MoneyDueDateNotificationSettings
import model.MoneyItem
import model.MoneyTags
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.Payment
import model.Share
import org.slf4j.LoggerFactory
import server.cache.Cacheable
import server.util.await
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val MONEY_COLLECTION = "money"
private const val SETTINGS_COLLECTION = "settings"
private const val DUE_DATE_NOTIFICATION_DOC = "money_due_date_notification"

class FirestoreMoneyRepository(
    private val firestore: Firestore,
) : MoneyRepository,
    Cacheable {
    private val logger = LoggerFactory.getLogger(FirestoreMoneyRepository::class.java)

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
                    "dueDate" to item.dueDate,
                    "shares" to
                        item.shares.map { s ->
                            mapOf("uid" to s.uid, "amount" to s.amount)
                        },
                )
            }

        val payments =
            data.payments.map { p ->
                mapOf(
                    "id" to p.id,
                    "uid" to p.uid,
                    "amount" to p.amount,
                    "paidAt" to p.paidAt,
                    "note" to p.note,
                    "isRedemption" to p.isRedemption,
                )
            }

        firestore
            .collection(MONEY_COLLECTION)
            .document(yearMonth)
            .set(mapOf("yearMonth" to yearMonth, "items" to items, "payments" to payments, "status" to data.status.name))
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
            filterTaggedItemsForImport(prevData?.items ?: emptyList(), tag, targetYearMonth)
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

    /**
     * Firestore ドキュメントから [MonthlyMoney] を組み立てる。
     *
     * 本リポジトリの不変条件として「ドキュメント ID == `yearMonth` フィールド値」を維持しており、
     * 呼び出し側は必ず `doc.id`（または同値の引数）を [yearMonth] に渡す。ドキュメント内に保存された
     * `"yearMonth"` フィールドは本関数では読まない（冗長保存だが、[saveMonthlyMoney] の `set` 全置換時に
     * スキーマとして一貫させるために書き込んでいる）。
     */
    private fun parseMonthlyMoney(
        yearMonth: String,
        doc: DocumentSnapshot,
    ): MonthlyMoney {
        val items = parseItems(doc.get("items"))
        // 新フィールド payments を優先、なければ旧名 paymentRecords を読む（マイグレーション過渡期の互換）
        val rawPayments = doc.get("payments") ?: doc.get("paymentRecords")
        val payments = parsePayments(rawPayments)
        val status = parseStatus(doc.getString("status"), doc.getBoolean("locked"))
        return MonthlyMoney(yearMonth = yearMonth, items = items, payments = payments, status = status)
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

    /**
     * 定期項目インポート対象の絞り込み + 複製時の dueDate 付け替え（テスト用に internal）。
     *
     * dueDate は前月の日付のまま複製すると、期日リマインダーが二度と一致せず永久に飛ばなくなる
     * （[MoneyDueDateNotificationService] は dueDate との完全一致でしか対象を検出しない）ため、
     * インポート先の月 [targetYearMonth] の同じ日に付け替える（毎月同じ日払いの家賃・サブスク等を
     * 想定）。[rebaseDueDateToTargetMonth] が対象月に存在しない日（月末クランプが必要なケース）を
     * 検出した場合は null にフォールバックする。id は呼び出し側（UUID 採番）で差し替えるため、
     * ここでは触れない。
     */
    internal fun filterTaggedItemsForImport(
        items: List<MoneyItem>,
        tag: String,
        targetYearMonth: String,
    ): List<MoneyItem> =
        items
            .filter { tag in it.tags }
            .map { item -> item.copy(dueDate = item.dueDate?.let { rebaseDueDateToTargetMonth(it, targetYearMonth) }) }

    /**
     * "YYYY-MM-DD" の日部分を維持したまま [targetYearMonth]（"YYYY-MM"）へ付け替える（テスト用に internal）。
     * 対象月にその日が存在しない（例: 1/31 → 2月、31日が無い）場合や dueDate の形式が不正な場合は
     * クランプせず null を返す（呼び出し側でユーザーに再設定してもらう）。
     */
    internal fun rebaseDueDateToTargetMonth(
        dueDate: String,
        targetYearMonth: String,
    ): String? {
        val day = dueDate.substringAfterLast('-').toIntOrNull() ?: return null
        val ym = runCatching { YearMonth.parse(targetYearMonth) }.getOrNull() ?: return null
        if (day < 1 || day > ym.lengthOfMonth()) return null
        return LocalDate.of(ym.year, ym.monthValue, day).toString()
    }

    /** Map リストから [MoneyItem] リストをパースする（テスト用に internal） */
    @Suppress("UNCHECKED_CAST")
    internal fun parseItems(raw: Any?): List<MoneyItem> {
        val itemsRaw = raw as? List<Map<String, Any?>> ?: return emptyList()
        return itemsRaw.map { entry ->
            // 新フィールド shares を優先、なければ旧名 payments を読む（マイグレーション過渡期の互換）
            val sharesRaw =
                (entry["shares"] as? List<Map<String, Any?>>)
                    ?: (entry["payments"] as? List<Map<String, Any?>>)
                    ?: emptyList()
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
                dueDate = entry["dueDate"] as? String,
                shares =
                    sharesRaw.map { s ->
                        Share(
                            uid = s["uid"] as String,
                            amount = (s["amount"] as Number).toLong(),
                        )
                    },
            )
        }
    }

    /** Map リストから [Payment] リストをパースする（テスト用に internal） */
    @Suppress("UNCHECKED_CAST")
    internal fun parsePayments(raw: Any?): List<Payment> {
        val paymentsRaw = raw as? List<Map<String, Any?>> ?: return emptyList()
        return paymentsRaw.map { p ->
            Payment(
                // マイグレーション前の Payment は id が未設定。空文字列として返し、フロント側の
                // `payment.id.isNotEmpty()` ガードで削除ボタンを非表示にする。
                id = p["id"] as? String ?: "",
                uid = p["uid"] as String,
                amount = (p["amount"] as Number).toLong(),
                paidAt = p["paidAt"] as String,
                note = p["note"] as? String ?: "",
                isRedemption = p["isRedemption"] as? Boolean ?: false,
            )
        }
    }

    override suspend fun getDueDateNotificationSettings(): MoneyDueDateNotificationSettings {
        val doc =
            firestore
                .collection(SETTINGS_COLLECTION)
                .document(DUE_DATE_NOTIFICATION_DOC)
                .get()
                .await()

        if (!doc.exists()) return MoneyDueDateNotificationSettings()

        return MoneyDueDateNotificationSettings(
            enabled = doc.getBoolean("enabled") ?: false,
            webhookUrl = doc.getString("webhookUrl") ?: "",
            daysBefore = (doc.getLong("daysBefore") ?: 1L).toInt(),
            notifyHour = (doc.getLong("notifyHour") ?: 23L).toInt(),
            prefix = doc.getString("prefix") ?: "",
        )
    }

    override suspend fun saveDueDateNotificationSettings(settings: MoneyDueDateNotificationSettings) {
        firestore
            .collection(SETTINGS_COLLECTION)
            .document(DUE_DATE_NOTIFICATION_DOC)
            .set(
                mapOf(
                    "enabled" to settings.enabled,
                    "webhookUrl" to settings.webhookUrl,
                    "daysBefore" to settings.daysBefore,
                    "notifyHour" to settings.notifyHour,
                    "prefix" to settings.prefix,
                ),
            ).await()
    }
}
