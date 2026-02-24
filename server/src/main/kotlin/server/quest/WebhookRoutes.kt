package server.quest

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.WebhookSettings
import org.koin.ktor.ext.inject
import server.auth.adminOnly

fun Route.webhookRoutes() {
    val webhookService by inject<WebhookService>()

    route("/settings/webhook") {
        adminOnly {
            get({
                tags = listOf("webhook")
                summary = "Webhook 設定取得（admin）"
                response {
                    code(HttpStatusCode.OK) {
                        body<WebhookSettings>()
                    }
                }
            }) {
                call.respond(webhookService.getSettings())
            }

            put({
                tags = listOf("webhook")
                summary = "Webhook 設定更新（admin）"
                request {
                    body<WebhookSettings>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<WebhookSettings>()
                    }
                }
            }) {
                val settings = call.receive<WebhookSettings>()
                webhookService.updateSettings(settings)
                call.respond(webhookService.getSettings())
            }
        }
    }
}
