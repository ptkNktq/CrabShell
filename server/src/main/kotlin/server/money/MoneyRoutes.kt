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
import model.MonthlyMoneyResponse
import model.MonthlyMoneySaveRequest
import model.MonthlyMoneyStatus
import model.MonthlyMoneyStatusUpdateRequest
import model.PayRequest
import org.koin.ktor.ext.inject
import server.auth.FirebaseAdmin
import server.auth.adminOnly
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.money.model.MonthlyMoneyRecord
import server.money.model.PaymentRecord

fun Route.moneyRoutes() {
    val moneyRepository by inject<MoneyRepository>()
    val moneyWebhookService by inject<MoneyWebhookService>()
    val paymentWebhookService by inject<PaymentWebhookService>()

    route("/money/{yearMonth}") {
        // 管理者: データ取得・全体保存
        adminOnly {
            get({
                tags = listOf("money")
                summary = "月次お金データ取得（admin）"
                request {
                    pathParameter<String>("yearMonth") { description = "年月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoneyResponse>()
                    }
                }
            }) {
                val yearMonth = call.parameters.getOrFail("yearMonth")
                val data = moneyRepository.getMonthlyMoney(yearMonth) ?: MonthlyMoneyRecord(yearMonth = yearMonth)
                call.respond(data.toResponse())
            }

            post("import-by-tag", {
                tags = listOf("money")
                summary = "前月からタグ付き項目をインポート（admin）"
                request {
                    pathParameter<String>("yearMonth") { description = "年月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoneyResponse>()
                    }
                    code(HttpStatusCode.Conflict) { description = "凍結中" }
                }
            }) {
                val yearMonth = call.parameters.getOrFail("yearMonth")
                val existing = moneyRepository.getMonthlyMoney(yearMonth)
                if (existing?.status == MonthlyMoneyStatus.FROZEN) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
                    return@post
                }
                val updated = moneyRepository.importItemsByTag(yearMonth, MoneyTags.RECURRING)
                call.respond(updated.toResponse())
            }

            put({
                tags = listOf("money")
                summary = "月次お金データ保存（admin）"
                description =
                    "items / paymentRecords を保存する。status はこのエンドポイントでは変更しない " +
                    "（既存値、新規月は PENDING を維持）。status を変更する場合は PATCH /status を使うこと。"
                request {
                    pathParameter<String>("yearMonth") { description = "年月（YYYY-MM）" }
                    body<MonthlyMoneySaveRequest>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoneyResponse>()
                    }
                    code(HttpStatusCode.Conflict) { description = "凍結中" }
                }
            }) {
                val yearMonth = call.parameters.getOrFail("yearMonth")
                val existing = moneyRepository.getMonthlyMoney(yearMonth) ?: MonthlyMoneyRecord(yearMonth = yearMonth)
                if (existing.status == MonthlyMoneyStatus.FROZEN) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
                    return@put
                }
                val request = call.receive<MonthlyMoneySaveRequest>()
                // 新規月の場合 existing.status は PENDING になる点に注意。
                val updated = request.toRecord(yearMonth = yearMonth, status = existing.status)
                moneyRepository.saveMonthlyMoney(yearMonth, updated)
                call.respond(updated.toResponse())
            }

            patch("status", {
                tags = listOf("money")
                summary = "月次ステータス更新（admin）"
                description =
                    "月次の MonthlyMoneyStatus を更新する。他エンドポイント（PUT / pay / redemption 等）が " +
                    "FROZEN の月を 409 で拒否するのに対し、このエンドポイントは FROZEN からの遷移（凍結解除）も " +
                    "含めた任意の状態遷移を admin 権限で許可する。凍結運用を admin が解除できる唯一の経路。"
                request {
                    pathParameter<String>("yearMonth") { description = "年月（YYYY-MM）" }
                    body<MonthlyMoneyStatusUpdateRequest>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoneyResponse>()
                    }
                }
            }) {
                val yearMonth = call.parameters.getOrFail("yearMonth")
                val request = call.receive<MonthlyMoneyStatusUpdateRequest>()
                val existing = moneyRepository.getMonthlyMoney(yearMonth) ?: MonthlyMoneyRecord(yearMonth = yearMonth)
                // FROZEN からの遷移も含めて admin に任意の状態遷移を許可する（凍結解除の唯一経路）。
                val updated = existing.copy(status = request.status)
                moneyRepository.saveMonthlyMoney(yearMonth, updated)
                // 同一ステータスへの冪等 PATCH での連打通知を避けるため、実際に遷移した場合のみ送信。
                if (existing.status != request.status && request.status == MonthlyMoneyStatus.CONFIRMED) {
                    moneyWebhookService.notifyConfirmed(yearMonth)
                }
                call.respond(updated.toResponse())
            }
        }

        // 一般ユーザー: 自分の割当のみ取得
        authenticated {
            get("my", {
                tags = listOf("money")
                summary = "自分の月次お金データ取得"
                request {
                    pathParameter<String>("yearMonth") { description = "年月（YYYY-MM）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoneyResponse>()
                    }
                }
            }) {
                val yearMonth = call.parameters.getOrFail("yearMonth")
                val uid = call.firebasePrincipal.uid

                val data = moneyRepository.getMonthlyMoney(yearMonth) ?: MonthlyMoneyRecord(yearMonth = yearMonth)
                call.respond(data.filterForUser(uid).toResponse())
            }
        }

        // 一般ユーザー: 支払い記録追加
        authenticated {
            post("pay", {
                tags = listOf("money")
                summary = "支払い記録追加"
                request {
                    pathParameter<String>("yearMonth") { description = "年月（YYYY-MM）" }
                    body<PayRequest>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MonthlyMoneyResponse>()
                    }
                    code(HttpStatusCode.NotFound) { description = "月データ未作成" }
                    code(HttpStatusCode.Conflict) { description = "凍結中" }
                }
            }) {
                val yearMonth = call.parameters.getOrFail("yearMonth")
                val uid = call.firebasePrincipal.uid
                val request = call.receive<PayRequest>()
                // 0 円・負額の入金は残債計算 (BalanceCalculationService) を歪めるうえ、
                // 0 円連投で Webhook 通知洪水を引き起こせるため、サーバー側で弾く。
                // /report/balances/redeem (ReportRoutes) と対称な制約。
                if (request.amount <= 0L) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "amount must be positive"))
                    return@post
                }
                // 永続化レコードはサーバー側で組み立てる。uid は principal、isRedemption は
                // /pay 経路では常に false（過払い精算は /report/balances/redeem 経由のみ）。
                val safeRecord =
                    PaymentRecord(
                        uid = uid,
                        amount = request.amount,
                        paidAt = request.paidAt,
                        isRedemption = false,
                    )

                val data = moneyRepository.getMonthlyMoney(yearMonth)
                if (data == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Month not found"))
                    return@post
                }

                if (data.status == MonthlyMoneyStatus.FROZEN) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Month is frozen"))
                    return@post
                }
                val updated = data.copy(paymentRecords = data.paymentRecords + safeRecord)
                moneyRepository.saveMonthlyMoney(yearMonth, updated)

                // displayName 未設定時に Firebase UID を Webhook 経路で外部チャネル（Discord/Slack）に
                // 流すと逆引き材料になりうるため、表示用フォールバックに置き換える。
                val payerName = FirebaseAdmin.getDisplayName(uid) ?: "不明なユーザー"
                paymentWebhookService.notifyPayment(
                    yearMonth = yearMonth,
                    payerName = payerName,
                    amount = safeRecord.amount,
                )

                call.respond(updated.filterForUser(uid).toResponse())
            }
        }
    }
}
