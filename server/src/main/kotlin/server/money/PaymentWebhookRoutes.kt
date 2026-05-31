package server.money

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.PaymentWebhookSettings
import org.koin.ktor.ext.inject
import server.auth.adminOnly

fun Route.paymentWebhookRoutes() {
    val paymentWebhookService by inject<PaymentWebhookService>()

    route("/settings/payment-webhook") {
        adminOnly {
            get({
                tags = listOf("payment-webhook")
                summary = "入金 Webhook 設定取得（admin）"
                response {
                    code(HttpStatusCode.OK) {
                        body<PaymentWebhookSettings>()
                    }
                }
            }) {
                call.respond(paymentWebhookService.getSettings())
            }

            put({
                tags = listOf("payment-webhook")
                summary = "入金 Webhook 設定更新（admin）"
                request {
                    body<PaymentWebhookSettings>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<PaymentWebhookSettings>()
                    }
                }
            }) {
                val settings = call.receive<PaymentWebhookSettings>()
                paymentWebhookService.updateSettings(settings)
                call.respond(paymentWebhookService.getSettings())
            }
        }
    }
}
