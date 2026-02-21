package server.report

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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

    // admin 専用: ユーザーごとの残額
    adminOnly {
        get("/report/balances") {
            val monthStr =
                call.request.queryParameters["month"]
                    ?: YearMonth.now().toString()

            val doc =
                firestore
                    .collection(MONEY_COLLECTION)
                    .document(monthStr)
                    .get()
                    .await()

            if (!doc.exists()) {
                call.respond(emptyList<UserBalance>())
                return@get
            }

            val items = parseItems(doc.get("items"))
            val records = parsePaymentRecords(doc.get("paymentRecords"))

            // ユーザーごとの割当額を集計
            val allocatedByUser = mutableMapOf<String, Long>()
            for (item in items) {
                for (payment in item.payments) {
                    allocatedByUser[payment.uid] =
                        (allocatedByUser[payment.uid] ?: 0L) + payment.amount
                }
            }

            // ユーザーごとの支払済み額を集計
            val paidByUser = mutableMapOf<String, Long>()
            for (record in records) {
                paidByUser[record.uid] =
                    (paidByUser[record.uid] ?: 0L) + record.amount
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
                    UserBalance(uid, displayName, allocated, paid, allocated - paid)
                }

            call.respond(balances)
        }
    }
}
