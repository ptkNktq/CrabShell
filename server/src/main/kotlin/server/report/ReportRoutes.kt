package server.report

import com.google.firebase.auth.FirebaseAuth
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import model.BalanceSummary
import model.ExpenseItem
import model.ExpenseReport
import model.MonthlyExpenseSummary
import model.MonthlyMoney
import model.OverpaymentRedemptionRequest
import model.PaymentRecord
import model.UserBalance
import org.koin.ktor.ext.inject
import server.auth.adminOnly
import server.auth.authenticated
import server.money.MoneyRepository
import java.time.Instant
import java.time.YearMonth

fun Route.reportRoutes() {
    val moneyRepository by inject<MoneyRepository>()

    authenticated {
        get("/report", {
            tags = listOf("report")
            summary = "支出レポート取得"
            request {
                queryParameter<String>("center") {
                    description = "中心月（YYYY-MM）"
                    required = false
                }
                queryParameter<Int>("range") {
                    description = "前後の月数（1-6、デフォルト3）"
                    required = false
                }
            }
            response {
                code(HttpStatusCode.OK) {
                    body<ExpenseReport>()
                }
            }
        }) {
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
                    val data = moneyRepository.getMonthlyMoney(monthStr)

                    if (data == null) {
                        MonthlyExpenseSummary(month = monthStr, totalAmount = 0L)
                    } else {
                        val expenseItems =
                            data.items.map { ExpenseItem(name = it.name, amount = it.amount, note = it.note) }
                        val totalAmount = data.items.sumOf { it.amount }
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
        get("/report/balances", {
            tags = listOf("report")
            summary = "過払い額サマリー取得（admin）"
            response {
                code(HttpStatusCode.OK) {
                    body<BalanceSummary>()
                }
            }
        }) {
            val allMonths = moneyRepository.getAllMonths()

            // 月ごとにユーザー別の割当額・支払額を集計
            val months = mutableListOf<String>()
            val overpaidByUser = mutableMapOf<String, Long>()
            val redeemedByUser = mutableMapOf<String, Long>()

            for (monthData in allMonths) {
                months.add(monthData.month)

                // この月のユーザー別割当額
                val monthAllocated = mutableMapOf<String, Long>()
                for (item in monthData.items) {
                    for (payment in item.payments) {
                        monthAllocated[payment.uid] =
                            (monthAllocated[payment.uid] ?: 0L) + payment.amount
                    }
                }

                // この月のユーザー別支払額（精算レコードを除外）
                val monthPaid = mutableMapOf<String, Long>()
                for (record in monthData.paymentRecords) {
                    if (record.isRedemption) {
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

        post("/report/balances/redeem", {
            tags = listOf("report")
            summary = "過払い金精算（admin）"
            request {
                body<OverpaymentRedemptionRequest>()
            }
            response {
                code(HttpStatusCode.OK) {
                    body<Map<String, String>>()
                }
                code(HttpStatusCode.BadRequest) { description = "不正なリクエスト" }
                code(HttpStatusCode.Conflict) { description = "ロック中" }
            }
        }) {
            val req = call.receive<OverpaymentRedemptionRequest>()

            if (req.amount <= 0L) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Amount must be positive"))
                return@post
            }
            runCatching { YearMonth.parse(req.month) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid month format"))
                return@post
            }

            val data = moneyRepository.getMonthlyMoney(req.month) ?: MonthlyMoney(month = req.month)
            if (data.locked) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is locked"))
                return@post
            }
            val record =
                PaymentRecord(
                    uid = req.uid,
                    amount = req.amount,
                    paidAt = Instant.now().toString(),
                    note = req.note,
                    isRedemption = true,
                )
            val updated = data.copy(paymentRecords = data.paymentRecords + record)
            moneyRepository.saveMonthlyMoney(req.month, updated)

            call.respond(mapOf("status" to "ok"))
        }
    }
}
