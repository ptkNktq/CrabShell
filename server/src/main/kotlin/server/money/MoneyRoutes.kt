package server.money

import com.google.firebase.cloud.FirestoreClient
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
import java.time.YearMonth

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"

fun Route.moneyRoutes() {
    route("/money/{month}") {
        // 管理者: データ取得・全体保存
        adminOnly {
            get {
                val month = call.parameters["month"]!!
                val doc = firestore.collection(MONEY_COLLECTION)
                    .document(month).get().get()

                if (!doc.exists()) {
                    // ドキュメントが存在しない場合、前月の recurring アイテムをコピー
                    val recurringItems = getRecurringItemsFromPreviousMonth(month)
                    call.respond(MonthlyMoney(month = month, items = recurringItems))
                    return@get
                }

                val items = parseItems(doc.get("items"))
                call.respond(MonthlyMoney(month = month, items = items))
            }

            put {
                val month = call.parameters["month"]!!
                val body = call.receive<MonthlyMoney>()
                saveMonthlyMoney(month, body)
                call.respond(body)
            }
        }

        // 一般ユーザー: 自分の割当のみ取得
        authenticated {
            get("my") {
                val month = call.parameters["month"]!!
                val uid = call.attributes[FirebaseTokenKey].uid
                val doc = firestore.collection(MONEY_COLLECTION)
                    .document(month).get().get()

                if (!doc.exists()) {
                    call.respond(MonthlyMoney(month = month))
                    return@get
                }

                val items = parseItems(doc.get("items"))
                // 自分に割当がある項目のみ
                val myItems = items.filter { item ->
                    item.payments.any { it.uid == uid }
                }
                call.respond(MonthlyMoney(month = month, items = myItems))
            }
        }

        // 一般ユーザー: 支払い記録追加
        authenticated {
            post("items/{itemId}/pay") {
                val month = call.parameters["month"]!!
                val itemId = call.parameters["itemId"]!!
                val uid = call.attributes[FirebaseTokenKey].uid
                val record = call.receive<PaymentRecord>()

                val doc = firestore.collection(MONEY_COLLECTION)
                    .document(month).get().get()

                if (!doc.exists()) {
                    call.respond(io.ktor.http.HttpStatusCode.NotFound, mapOf("error" to "Month not found"))
                    return@post
                }

                val items = parseItems(doc.get("items"))
                val updatedItems = items.map { item ->
                    if (item.id == itemId) {
                        val updatedPayments = item.payments.map { payment ->
                            if (payment.uid == uid) {
                                payment.copy(records = payment.records + record)
                            } else {
                                payment
                            }
                        }
                        item.copy(payments = updatedPayments)
                    } else {
                        item
                    }
                }

                val data = MonthlyMoney(month = month, items = updatedItems)
                saveMonthlyMoney(month, data)
                call.respond(data)
            }
        }
    }
}

private fun saveMonthlyMoney(month: String, data: MonthlyMoney) {
    val items = data.items.map { item ->
        mapOf(
            "id" to item.id,
            "name" to item.name,
            "amount" to item.amount,
            "note" to item.note,
            "recurring" to item.recurring,
            "payments" to item.payments.map { p ->
                mapOf(
                    "uid" to p.uid,
                    "amount" to p.amount,
                    "records" to p.records.map { r ->
                        mapOf("amount" to r.amount, "paidAt" to r.paidAt)
                    },
                )
            },
        )
    }

    firestore.collection(MONEY_COLLECTION)
        .document(month)
        .set(mapOf("month" to month, "items" to items))
        .get()
}

@Suppress("UNCHECKED_CAST")
private fun parseItems(raw: Any?): List<MoneyItem> {
    val itemsRaw = raw as? List<Map<String, Any?>> ?: return emptyList()
    return itemsRaw.map { entry ->
        val paymentsRaw = entry["payments"] as? List<Map<String, Any?>> ?: emptyList()
        MoneyItem(
            id = entry["id"] as String,
            name = entry["name"] as String,
            amount = (entry["amount"] as Number).toLong(),
            note = entry["note"] as? String ?: "",
            recurring = entry["recurring"] as? Boolean ?: false,
            payments = paymentsRaw.map { p ->
                val recordsRaw = p["records"] as? List<Map<String, Any?>> ?: emptyList()
                Payment(
                    uid = p["uid"] as String,
                    amount = (p["amount"] as Number).toLong(),
                    records = recordsRaw.map { r ->
                        PaymentRecord(
                            amount = (r["amount"] as Number).toLong(),
                            paidAt = r["paidAt"] as String,
                        )
                    },
                )
            },
        )
    }
}

private fun getRecurringItemsFromPreviousMonth(month: String): List<MoneyItem> {
    val previousMonth = YearMonth.parse(month).minusMonths(1).toString()
    val prevDoc = firestore.collection(MONEY_COLLECTION)
        .document(previousMonth).get().get()

    if (!prevDoc.exists()) return emptyList()

    return parseItems(prevDoc.get("items"))
        .filter { it.recurring }
        .map { item ->
            // recurring コピー時は records をリセット
            item.copy(
                id = java.util.UUID.randomUUID().toString(),
                payments = item.payments.map { it.copy(records = emptyList()) },
            )
        }
}
