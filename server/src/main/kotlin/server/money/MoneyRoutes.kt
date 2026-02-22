package server.money

import com.google.firebase.cloud.FirestoreClient
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.PaymentRecord
import server.auth.FirebaseTokenKey
import server.auth.adminOnly
import server.auth.authenticated
import server.util.await
import java.time.YearMonth

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"

fun Route.moneyRoutes() {
    route("/money/{month}") {
        // 管理者: データ取得・全体保存
        adminOnly {
            get({
                tags = listOf("money")
                summary = "月次お金データ取得（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters["month"]!!
                val doc =
                    firestore
                        .collection(MONEY_COLLECTION)
                        .document(month)
                        .get()
                        .await()

                if (!doc.exists()) {
                    val recurringItems = getRecurringItemsFromPreviousMonth(month)
                    call.respond(MonthlyMoney(month = month, items = recurringItems))
                    return@get
                }

                call.respond(parseMonthlyMoney(month, doc))
            }

            put({
                tags = listOf("money")
                summary = "月次お金データ保存（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                    body<MonthlyMoney>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                    code(HttpStatusCode.Conflict) { description = "ロック中" }
                }
            }) {
                val month = call.parameters["month"]!!
                val existing = getMonthlyMoney(month)
                if (existing.locked) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is locked"))
                    return@put
                }
                val body = call.receive<MonthlyMoney>()
                saveMonthlyMoney(month, body)
                call.respond(body)
            }

            patch("lock", {
                tags = listOf("money")
                summary = "月次ロック切り替え（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters["month"]!!
                val existing = getMonthlyMoney(month)
                val updated = existing.copy(locked = !existing.locked)
                saveMonthlyMoney(month, updated)
                call.respond(updated)
            }
        }

        // 一般ユーザー: 自分の割当のみ取得
        authenticated {
            get("my", {
                tags = listOf("money")
                summary = "自分の月次お金データ取得"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters["month"]!!
                val uid = call.attributes[FirebaseTokenKey].uid
                val doc =
                    firestore
                        .collection(MONEY_COLLECTION)
                        .document(month)
                        .get()
                        .await()

                if (!doc.exists()) {
                    call.respond(MonthlyMoney(month = month))
                    return@get
                }

                val data = parseMonthlyMoney(month, doc)
                // 自分に割当がある項目のみ + 自分の支払い記録のみ
                val myItems =
                    data.items.filter { item ->
                        item.payments.any { it.uid == uid }
                    }
                val myRecords = data.paymentRecords.filter { it.uid == uid }
                call.respond(MonthlyMoney(month = month, items = myItems, paymentRecords = myRecords, locked = data.locked))
            }
        }

        // 一般ユーザー: 支払い記録追加
        authenticated {
            post("pay", {
                tags = listOf("money")
                summary = "支払い記録追加"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                    body<PaymentRecord>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                    code(HttpStatusCode.NotFound) { description = "月データ未作成" }
                    code(HttpStatusCode.Conflict) { description = "ロック中" }
                }
            }) {
                val month = call.parameters["month"]!!
                val uid = call.attributes[FirebaseTokenKey].uid
                val record = call.receive<PaymentRecord>()
                // uid をサーバー側で上書き（改ざん防止）
                val safeRecord = record.copy(uid = uid)

                val doc =
                    firestore
                        .collection(MONEY_COLLECTION)
                        .document(month)
                        .get()
                        .await()

                if (!doc.exists()) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Month not found"))
                    return@post
                }

                val data = parseMonthlyMoney(month, doc)
                if (data.locked) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is locked"))
                    return@post
                }
                val updated = data.copy(paymentRecords = data.paymentRecords + safeRecord)
                saveMonthlyMoney(month, updated)

                // 呼び出し元に自分のデータのみ返す
                val myItems =
                    updated.items.filter { item ->
                        item.payments.any { it.uid == uid }
                    }
                val myRecords = updated.paymentRecords.filter { it.uid == uid }
                call.respond(MonthlyMoney(month = month, items = myItems, paymentRecords = myRecords, locked = updated.locked))
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun parseMonthlyMoney(
    month: String,
    doc: com.google.cloud.firestore.DocumentSnapshot,
): MonthlyMoney {
    val items = parseItems(doc.get("items"))
    val records = parsePaymentRecords(doc.get("paymentRecords"))
    val locked = doc.getBoolean("locked") ?: false
    return MonthlyMoney(month = month, items = items, paymentRecords = records, locked = locked)
}

internal suspend fun getMonthlyMoney(month: String): MonthlyMoney {
    val doc =
        firestore
            .collection(MONEY_COLLECTION)
            .document(month)
            .get()
            .await()
    if (!doc.exists()) return MonthlyMoney(month = month)
    return parseMonthlyMoney(month, doc)
}

internal suspend fun saveMonthlyMoney(
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

private suspend fun getRecurringItemsFromPreviousMonth(month: String): List<MoneyItem> {
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
