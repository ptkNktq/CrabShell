package server.report

import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import model.ExpenseItem
import model.ExpenseReport
import model.MonthlyExpenseSummary
import server.auth.authenticated
import server.money.parseItems
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
}
