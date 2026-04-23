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
import model.MoneyTags
import model.MonthlyExpenseSummary
import model.MonthlyMoney
import model.MonthlyMoneyStatus
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
    val balanceService by inject<BalanceCalculationService>()

    authenticated {
        get("/report", {
            tags = listOf("report")
            summary = "支出レポート取得"
            request {
                queryParameter<String>("center") {
                    description = "中心となる年月（YYYY-MM）"
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
                    val yearMonthStr = ym.toString()
                    val data = moneyRepository.getMonthlyMoney(yearMonthStr)

                    buildExpenseSummary(yearMonthStr, data)
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
            val result = balanceService.calculateOverpayments(allMonths)

            val balances =
                result.overpayments.mapNotNull { overpayment ->
                    if (overpayment.net <= 0L) return@mapNotNull null
                    val displayName =
                        try {
                            FirebaseAuth.getInstance().getUser(overpayment.uid).displayName ?: overpayment.uid
                        } catch (_: Exception) {
                            overpayment.uid
                        }
                    UserBalance(overpayment.uid, displayName, 0L, 0L, overpayment.net)
                }

            call.respond(
                BalanceSummary(
                    balances = balances,
                    periodStart = result.yearMonths.firstOrNull() ?: "",
                    periodEnd = result.yearMonths.lastOrNull() ?: "",
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
                code(HttpStatusCode.Conflict) { description = "凍結中" }
            }
        }) {
            val req = call.receive<OverpaymentRedemptionRequest>()

            if (req.amount <= 0L) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Amount must be positive"))
                return@post
            }
            runCatching { YearMonth.parse(req.yearMonth) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid yearMonth format"))
                return@post
            }

            val data = moneyRepository.getMonthlyMoney(req.yearMonth) ?: MonthlyMoney(yearMonth = req.yearMonth)
            if (data.status == MonthlyMoneyStatus.FROZEN) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
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
            moneyRepository.saveMonthlyMoney(req.yearMonth, updated)

            call.respond(mapOf("status" to "ok"))
        }
    }
}

/** MonthlyMoney から繰越タグ付き項目を除外して MonthlyExpenseSummary を構築する */
internal fun buildExpenseSummary(
    yearMonth: String,
    data: MonthlyMoney?,
): MonthlyExpenseSummary {
    if (data == null) return MonthlyExpenseSummary(yearMonth = yearMonth, totalAmount = 0L)
    val reportItems = data.items.filter { MoneyTags.CARRY_OVER !in it.tags }
    return MonthlyExpenseSummary(
        yearMonth = yearMonth,
        totalAmount = reportItems.sumOf { it.amount },
        items = reportItems.map { ExpenseItem(name = it.name, amount = it.amount, note = it.note) },
    )
}
