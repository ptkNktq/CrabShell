package server.report

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import model.BalanceSummary
import model.ExpenseItem
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.OverpaymentRedemptionRequest
import model.PaymentRecord
import model.UserBalance
import server.auth.adminOnly
import server.auth.authenticated
import server.money.getMonthlyMoney
import server.money.parseItems
import server.money.parsePaymentRecords
import server.money.saveMonthlyMoney
import server.util.await
import java.time.Instant
import java.time.YearMonth

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val MONEY_COLLECTION = "money"
private const val REDEMPTION_NOTE = "過払い金から支払い"

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

    // admin 専用: ユーザーごとの過払い額（月ごとに判定、過払い月のみ合算）
    adminOnly {
        get("/report/balances") {
            val docs =
                firestore
                    .collection(MONEY_COLLECTION)
                    .get()
                    .await()

            // 月ごとにユーザー別の割当額・支払額を集計
            val months = mutableListOf<String>()
            val overpaidByUser = mutableMapOf<String, Long>()
            val redeemedByUser = mutableMapOf<String, Long>()
            val allUids = mutableSetOf<String>()

            for (doc in docs.documents) {
                months.add(doc.id)
                val items = parseItems(doc.get("items"))
                val records = parsePaymentRecords(doc.get("paymentRecords"))

                // この月のユーザー別割当額
                val monthAllocated = mutableMapOf<String, Long>()
                for (item in items) {
                    for (payment in item.payments) {
                        allUids.add(payment.uid)
                        monthAllocated[payment.uid] =
                            (monthAllocated[payment.uid] ?: 0L) + payment.amount
                    }
                }

                // この月のユーザー別支払額（精算レコードを除外）
                val monthPaid = mutableMapOf<String, Long>()
                for (record in records) {
                    allUids.add(record.uid)
                    if (record.note == REDEMPTION_NOTE) {
                        redeemedByUser[record.uid] =
                            (redeemedByUser[record.uid] ?: 0L) + record.amount
                    } else {
                        monthPaid[record.uid] =
                            (monthPaid[record.uid] ?: 0L) + record.amount
                    }
                }

                // 過払い月のみ加算
                val uidsInMonth = monthAllocated.keys + monthPaid.keys
                for (uid in uidsInMonth) {
                    val diff = (monthPaid[uid] ?: 0L) - (monthAllocated[uid] ?: 0L)
                    if (diff > 0L) {
                        overpaidByUser[uid] = (overpaidByUser[uid] ?: 0L) + diff
                    }
                }
            }

            val balances =
                overpaidByUser.mapNotNull { (uid, overpaid) ->
                    val redeemed = redeemedByUser[uid] ?: 0L
                    val net = (overpaid - redeemed).coerceAtLeast(0L)
                    if (net <= 0L) return@mapNotNull null
                    val displayName =
                        try {
                            FirebaseAuth.getInstance().getUser(uid).displayName ?: uid
                        } catch (_: Exception) {
                            uid
                        }
                    UserBalance(uid, displayName, 0L, 0L, net)
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

        post("/report/balances/redeem") {
            val req = call.receive<OverpaymentRedemptionRequest>()

            if (req.amount <= 0L) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Amount must be positive"))
                return@post
            }
            runCatching { YearMonth.parse(req.month) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid month format"))
                return@post
            }

            val data = getMonthlyMoney(req.month)
            if (data.locked) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is locked"))
                return@post
            }
            val record =
                PaymentRecord(
                    uid = req.uid,
                    amount = req.amount,
                    paidAt = Instant.now().toString(),
                    note = REDEMPTION_NOTE,
                )
            val updated = data.copy(paymentRecords = data.paymentRecords + record)
            saveMonthlyMoney(req.month, updated)

            call.respond(mapOf("status" to "ok"))
        }
    }
}
