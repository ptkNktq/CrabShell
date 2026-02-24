package server.money

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import model.MonthlyMoney
import model.PaymentRecord
import org.koin.ktor.ext.inject
import server.auth.adminOnly
import server.auth.authenticated
import server.auth.firebasePrincipal

fun Route.moneyRoutes() {
    val moneyRepository by inject<MoneyRepository>()

    route("/money/{month}") {
        // 管理者: データ取得・全体保存
        adminOnly {
            get({
                tags = listOf("money")
                summary = "月次お金データ取得（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val data = moneyRepository.getMonthlyMoney(month)

                if (data == null) {
                    val recurringItems = moneyRepository.getRecurringItemsFromPreviousMonth(month)
                    call.respond(MonthlyMoney(month = month, items = recurringItems))
                    return@get
                }

                call.respond(data)
            }

            put({
                tags = listOf("money")
                summary = "月次お金データ保存（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                    body<MonthlyMoney>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                    code(HttpStatusCode.Conflict) { description = "ロック中" }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val existing = moneyRepository.getMonthlyMoney(month) ?: MonthlyMoney(month = month)
                if (existing.locked) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is locked"))
                    return@put
                }
                val body = call.receive<MonthlyMoney>()
                moneyRepository.saveMonthlyMoney(month, body)
                call.respond(body)
            }

            patch("lock", {
                tags = listOf("money")
                summary = "月次ロック切り替え（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val existing = moneyRepository.getMonthlyMoney(month) ?: MonthlyMoney(month = month)
                val updated = existing.copy(locked = !existing.locked)
                moneyRepository.saveMonthlyMoney(month, updated)
                call.respond(updated)
            }
        }

        // 一般ユーザー: 自分の割当のみ取得
        authenticated {
            get("my", {
                tags = listOf("money")
                summary = "自分の月次お金データ取得"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val uid = call.firebasePrincipal.uid

                val data = moneyRepository.getMonthlyMoney(month)
                if (data == null) {
                    call.respond(MonthlyMoney(month = month))
                    return@get
                }

                // 自分に割当がある項目のみ + 自分の支払い記録のみ
                val myItems =
                    data.items.filter { item ->
                        item.payments.any { it.uid == uid }
                    }
                val myRecords = data.paymentRecords.filter { it.uid == uid }
                call.respond(MonthlyMoney(month = month, items = myItems, paymentRecords = myRecords, locked = data.locked))
            }
        }

        // 一般ユーザー: 支払い記録追加
        authenticated {
            post("pay", {
                tags = listOf("money")
                summary = "支払い記録追加"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                    body<PaymentRecord>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                    code(HttpStatusCode.NotFound) { description = "月データ未作成" }
                    code(HttpStatusCode.Conflict) { description = "ロック中" }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val uid = call.firebasePrincipal.uid
                val record = call.receive<PaymentRecord>()
                // uid をサーバー側で上書き（改ざん防止）
                val safeRecord = record.copy(uid = uid)

                val data = moneyRepository.getMonthlyMoney(month)
                if (data == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Month not found"))
                    return@post
                }

                if (data.locked) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is locked"))
                    return@post
                }
                val updated = data.copy(paymentRecords = data.paymentRecords + safeRecord)
                moneyRepository.saveMonthlyMoney(month, updated)

                // 呼び出し元に自分のデータのみ返す
                val myItems =
                    updated.items.filter { item ->
                        item.payments.any { it.uid == uid }
                    }
                val myRecords = updated.paymentRecords.filter { it.uid == uid }
                call.respond(MonthlyMoney(month = month, items = myItems, paymentRecords = myRecords, locked = updated.locked))
            }
        }
    }
}
