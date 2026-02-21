package server.report

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import model.BalanceSummary
import model.ExpenseItem
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.UserBalance
import server.auth.adminOnly
import server.auth.authenticated
import server.money.parseItems
import server.money.parsePaymentRecords
import server.util.await
import java.time.YearMonth

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"

fun Route.reportRoutes() {
    authenticated {
        get("/report") {
            val centerStr = call.request.queryParameters["center"]
            val range =
                call.request.queryParameters["range"]
                    ?.toIntOrNull()
                    ?.coerceIn(1, 6) ?: 3
            val center =
                if (centerStr != null) {
                    runCatching { YearMonth.parse(centerStr) }.getOrElse { YearMonth.now() }
                } else {
                    YearMonth.now()
                }

            val summaries =
                (-range..range).map { offset ->
                    val ym = center.plusMonths(offset.toLong())
                    val monthStr = ym.toString()
                    val doc =
                        firestore
                            .collection(MONEY_COLLECTION)
                            .document(monthStr)
                            .get()
                            .await()

                    if (!doc.exists()) {
                        MonthlyExpenseSummary(month = monthStr, totalAmount = 0L)
                    } else {
                        val items = parseItems(doc.get("items"))
                        val expenseItems =
                            items.map { ExpenseItem(name = it.name, amount = it.amount, note = it.note) }
                        val totalAmount = items.sumOf { it.amount }
                        MonthlyExpenseSummary(
                            month = monthStr,
                            totalAmount = totalAmount,
                            items = expenseItems,
                        )
                    }
                }

            call.respond(ExpenseReport(months = summaries))
        }
    }

    // admin 専用: ユーザーごとの残額（全期間累計）
    adminOnly {
        get("/report/balances") {
            val docs =
                firestore
                    .collection(MONEY_COLLECTION)
                    .get()
                    .await()

            val allocatedByUser = mutableMapOf<String, Long>()
            val paidByUser = mutableMapOf<String, Long>()
            val months = mutableListOf<String>()

            for (doc in docs.documents) {
                months.add(doc.id)
                val items = parseItems(doc.get("items"))
                val records = parsePaymentRecords(doc.get("paymentRecords"))

                for (item in items) {
                    for (payment in item.payments) {
                        allocatedByUser[payment.uid] =
                            (allocatedByUser[payment.uid] ?: 0L) + payment.amount
                    }
                }

                for (record in records) {
                    paidByUser[record.uid] =
                        (paidByUser[record.uid] ?: 0L) + record.amount
                }
            }

            val allUids = allocatedByUser.keys + paidByUser.keys
            val balances =
                allUids.map { uid ->
                    val displayName =
                        try {
                            FirebaseAuth.getInstance().getUser(uid).displayName ?: uid
                        } catch (_: Exception) {
                            uid
                        }
                    val allocated = allocatedByUser[uid] ?: 0L
                    val paid = paidByUser[uid] ?: 0L
                    UserBalance(uid, displayName, allocated, paid, paid - allocated)
                }

            months.sort()
            call.respond(
                BalanceSummary(
                    balances = balances,
                    periodStart = months.firstOrNull() ?: "",
                    periodEnd = months.lastOrNull() ?: "",
                ),
            )
        }
    }
}
