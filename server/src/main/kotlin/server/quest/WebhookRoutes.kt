package server.quest

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import model.WebhookSettings
import server.auth.adminOnly

fun Route.webhookRoutes() {
    route("/settings/webhook") {
        adminOnly {
            get {
                call.respond(WebhookService.getSettings())
            }

            put {
                val settings = call.receive<WebhookSettings>()
                WebhookService.updateSettings(settings)
                call.respond(WebhookService.getSettings())
            }
        }
    }
}
