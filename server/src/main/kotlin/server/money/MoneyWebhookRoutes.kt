package server.money

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.MoneyWebhookSettings
import org.koin.ktor.ext.inject
import server.auth.adminOnly

fun Route.moneyWebhookRoutes() {
    val moneyWebhookService by inject<MoneyWebhookService>()

    route("/settings/money-webhook") {
        adminOnly {
            get({
                tags = listOf("money-webhook")
                summary = "お金 Webhook 設定取得（admin）"
                response {
                    code(HttpStatusCode.OK) {
                        body<MoneyWebhookSettings>()
                    }
                }
            }) {
                call.respond(moneyWebhookService.getSettings())
            }

            put({
                tags = listOf("money-webhook")
                summary = "お金 Webhook 設定更新（admin）"
                request {
                    body<MoneyWebhookSettings>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<MoneyWebhookSettings>()
                    }
                }
            }) {
                val settings = call.receive<MoneyWebhookSettings>()
                moneyWebhookService.updateSettings(settings)
                call.respond(moneyWebhookService.getSettings())
            }
        }
    }
}
