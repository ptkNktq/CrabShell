package server.money

import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import server.auth.adminOnly
import java.time.YearMonth

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"

fun Route.moneyRoutes() {
    route("/money/{month}") {
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

                val items = body.items.map { item ->
                    mapOf(
                        "id" to item.id,
                        "name" to item.name,
                        "amount" to item.amount,
                        "note" to item.note,
                        "recurring" to item.recurring,
                        "payments" to item.payments.map { p ->
                            mapOf("uid" to p.uid, "amount" to p.amount)
                        },
                    )
                }

                firestore.collection(MONEY_COLLECTION)
                    .document(month)
                    .set(mapOf("month" to month, "items" to items))
                    .get()

                call.respond(body)
            }
        }
    }
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
                Payment(
                    uid = p["uid"] as String,
                    amount = (p["amount"] as Number).toLong(),
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
        .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
}
