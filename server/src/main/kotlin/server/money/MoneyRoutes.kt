package server.money

import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import server.auth.adminOnly
import server.auth.authenticated

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"

fun Route.moneyRoutes() {
    route("/money/{month}") {
        authenticated {
            get {
                val month = call.parameters["month"]!!
                val doc = firestore.collection(MONEY_COLLECTION)
                    .document(month).get().get()

                if (!doc.exists()) {
                    call.respond(MonthlyMoney(month = month))
                    return@get
                }

                @Suppress("UNCHECKED_CAST")
                val itemsRaw = doc.get("items") as? List<Map<String, Any?>> ?: emptyList()
                val items = itemsRaw.map { entry ->
                    @Suppress("UNCHECKED_CAST")
                    val paymentsRaw = entry["payments"] as? List<Map<String, Any?>> ?: emptyList()
                    MoneyItem(
                        id = entry["id"] as String,
                        name = entry["name"] as String,
                        amount = (entry["amount"] as Number).toLong(),
                        note = entry["note"] as? String ?: "",
                        payments = paymentsRaw.map { p ->
                            Payment(
                                uid = p["uid"] as String,
                                amount = (p["amount"] as Number).toLong(),
                            )
                        },
                    )
                }
                call.respond(MonthlyMoney(month = month, items = items))
            }
        }

        adminOnly {
            put {
                val month = call.parameters["month"]!!
                val body = call.receive<MonthlyMoney>()

                val items = body.items.map { item ->
                    mapOf(
                        "id" to item.id,
                        "name" to item.name,
                        "amount" to item.amount,
                        "note" to item.note,
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
