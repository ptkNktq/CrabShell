package server.money

import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.PaymentRecord
import server.firestore.FirestoreProvider
import server.util.await
import java.time.YearMonth

private const val MONEY_COLLECTION = "money"

/** Money データの Firestore アクセスを集約するリポジトリ */
object MoneyRepository {
    private val firestore get() = FirestoreProvider.instance

    suspend fun getMonthlyMoney(month: String): MonthlyMoney {
        val doc =
            firestore
                .collection(MONEY_COLLECTION)
                .document(month)
                .get()
                .await()
        if (!doc.exists()) return MonthlyMoney(month = month)
        return parseMonthlyMoney(month, doc)
    }

    suspend fun exists(month: String): Boolean =
        firestore
            .collection(MONEY_COLLECTION)
            .document(month)
            .get()
            .await()
            .exists()

    suspend fun saveMonthlyMoney(
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
                    "recurring" to item.recurring,
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
            .set(mapOf("month" to month, "items" to items, "paymentRecords" to records, "locked" to data.locked))
            .await()
    }

    suspend fun getRecurringItemsFromPreviousMonth(month: String): List<MoneyItem> {
        val previousMonth = YearMonth.parse(month).minusMonths(1).toString()
        val prevDoc =
            firestore
                .collection(MONEY_COLLECTION)
                .document(previousMonth)
                .get()
                .await()

        if (!prevDoc.exists()) return emptyList()

        return parseItems(prevDoc.get("items"))
            .filter { it.recurring }
            .map { item ->
                item.copy(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                )
            }
    }

    /** レポート用: 全月のデータを取得 */
    suspend fun getAllMonths(): List<MonthlyMoney> {
        val docs =
            firestore
                .collection(MONEY_COLLECTION)
                .get()
                .await()
                .documents
        return docs.map { doc -> parseMonthlyMoney(doc.id, doc) }
    }

    private fun parseMonthlyMoney(
        month: String,
        doc: com.google.cloud.firestore.DocumentSnapshot,
    ): MonthlyMoney {
        val items = parseItems(doc.get("items"))
        val records = parsePaymentRecords(doc.get("paymentRecords"))
        val locked = doc.getBoolean("locked") ?: false
        return MonthlyMoney(month = month, items = items, paymentRecords = records, locked = locked)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseItems(raw: Any?): List<MoneyItem> {
        val itemsRaw = raw as? List<Map<String, Any?>> ?: return emptyList()
        return itemsRaw.map { entry ->
            val paymentsRaw = entry["payments"] as? List<Map<String, Any?>> ?: emptyList()
            MoneyItem(
                id = entry["id"] as String,
                name = entry["name"] as String,
                amount = (entry["amount"] as Number).toLong(),
                note = entry["note"] as? String ?: "",
                recurring = entry["recurring"] as? Boolean ?: false,
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
}
