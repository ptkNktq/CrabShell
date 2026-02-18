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
import java.time.YearMonth

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"

fun Route.reportRoutes() {
    authenticated {
        get("/report") {
            val monthsParam = call.request.queryParameters["months"]?.toIntOrNull()?.coerceIn(1, 12) ?: 6
            val now = YearMonth.now()

            val summaries =
                (0 until monthsParam).map { offset ->
                    val ym = now.minusMonths(offset.toLong())
                    val monthStr = ym.toString()
                    val doc =
                        firestore.collection(MONEY_COLLECTION)
                            .document(monthStr).get().get()

                    if (!doc.exists()) {
                        MonthlyExpenseSummary(month = monthStr, totalAmount = 0L)
                    } else {
                        val items = parseItems(doc.get("items"))
                        val expenseItems =
                            items.map { ExpenseItem(name = it.name, amount = it.amount) }
                        val totalAmount = items.sumOf { it.amount }
                        MonthlyExpenseSummary(
                            month = monthStr,
                            totalAmount = totalAmount,
                            items = expenseItems,
                        )
                    }
                }.sortedBy { it.month }

            call.respond(ExpenseReport(months = summaries))
        }
    }
}
