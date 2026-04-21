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
import model.MoneyTags
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.MonthlyMoneyStatusUpdate
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
                call.respond(data ?: MonthlyMoney(month = month))
            }

            post("import-by-tag", {
                tags = listOf("money")
                summary = "前月からタグ付き項目をインポート（admin）"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                    code(HttpStatusCode.Conflict) { description = "凍結中" }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val existing = moneyRepository.getMonthlyMoney(month)
                if (existing?.status == MonthlyMoneyStatus.FROZEN) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
                    return@post
                }
                val updated = moneyRepository.importItemsByTag(month, MoneyTags.RECURRING)
                call.respond(updated)
            }

            put({
                tags = listOf("money")
                summary = "月次お金データ保存（admin）"
                description =
                    "items / paymentRecords を保存する。body.status は無視され、" +
                    "既存値（新規月は PENDING）が維持される。status を変更する場合は PATCH /status を使うこと。"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                    body<MonthlyMoney>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                    code(HttpStatusCode.Conflict) { description = "凍結中" }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val existing = moneyRepository.getMonthlyMoney(month) ?: MonthlyMoney(month = month)
                if (existing.status == MonthlyMoneyStatus.FROZEN) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
                    return@put
                }
                val body = call.receive<MonthlyMoney>()
                // status はこのエンドポイントでは変更しない（専用 PATCH /status で更新）。
                // 新規月の場合 existing.status は PENDING になる点に注意。
                val normalized = body.copy(status = existing.status)
                moneyRepository.saveMonthlyMoney(month, normalized)
                call.respond(normalized)
            }

            patch("status", {
                tags = listOf("money")
                summary = "月次ステータス更新（admin）"
                description =
                    "月次の MonthlyMoneyStatus を更新する。他エンドポイント（PUT / pay / redemption 等）が " +
                    "FROZEN の月を 409 で拒否するのに対し、このエンドポイントは FROZEN からの遷移（凍結解除）も " +
                    "含めた任意の状態遷移を admin 権限で許可する。凍結運用を admin が解除できる唯一の経路。"
                request {
                    pathParameter<String>("month") { description = "月（YYYY-MM）" }
                    body<MonthlyMoneyStatusUpdate>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoney>()
                    }
                }
            }) {
                val month = call.parameters.getOrFail("month")
                val body = call.receive<MonthlyMoneyStatusUpdate>()
                val existing = moneyRepository.getMonthlyMoney(month) ?: MonthlyMoney(month = month)
                // FROZEN からの遷移も含めて admin に任意の状態遷移を許可する（凍結解除の唯一経路）。
                val updated = existing.copy(status = body.status)
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

                call.respond(data.filterForUser(uid))
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
                    code(HttpStatusCode.Conflict) { description = "凍結中" }
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

                if (data.status == MonthlyMoneyStatus.FROZEN) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
                    return@post
                }
                val updated = data.copy(paymentRecords = data.paymentRecords + safeRecord)
                moneyRepository.saveMonthlyMoney(month, updated)

                call.respond(updated.filterForUser(uid))
            }
        }
    }
}
