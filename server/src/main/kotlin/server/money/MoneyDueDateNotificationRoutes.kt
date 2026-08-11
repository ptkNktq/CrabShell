package server.money

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.MoneyDueDateNotificationSettings
import org.koin.ktor.ext.inject
import server.auth.adminOnly
import server.auth.authenticated

fun Route.moneyDueDateNotificationRoutes() {
    val moneyRepository by inject<MoneyRepository>()
    val moneyDueDateNotificationService by inject<MoneyDueDateNotificationService>()

    route("/money/due-date-notification-settings") {
        authenticated {
            get({
                tags = listOf("money")
                summary = "支払期日リマインダー設定取得"
                response {
                    code(HttpStatusCode.OK) {
                        body<MoneyDueDateNotificationSettings>()
                    }
                }
            }) {
                call.respond(moneyRepository.getDueDateNotificationSettings())
            }
        }

        adminOnly {
            put({
                tags = listOf("money")
                summary = "支払期日リマインダー設定更新（admin）"
                request {
                    body<MoneyDueDateNotificationSettings>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MoneyDueDateNotificationSettings>()
                    }
                    code(HttpStatusCode.BadRequest) { description = "daysBefore / notifyHour が範囲外" }
                }
            }) {
                val settings = call.receive<MoneyDueDateNotificationSettings>()
                if (settings.notifyHour !in 0..23) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "notifyHour must be 0-23"))
                    return@put
                }
                if (settings.daysBefore < 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "daysBefore must be 0 or more"))
                    return@put
                }
                moneyRepository.saveDueDateNotificationSettings(settings)
                call.respond(settings)
            }

            post("test", {
                tags = listOf("money")
                summary = "支払期日リマインダーのテスト送信（admin）"
                response {
                    code(HttpStatusCode.NoContent) { description = "送信成功" }
                    code(HttpStatusCode.BadRequest) { description = "Webhook URL 未設定" }
                }
            }) {
                try {
                    moneyDueDateNotificationService.sendTestNotification()
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "送信失敗")))
                }
            }
        }
    }
}
