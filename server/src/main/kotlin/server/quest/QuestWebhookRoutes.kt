package server.quest

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.QuestWebhookSettings
import org.koin.ktor.ext.inject
import server.auth.adminOnly

fun Route.questWebhookRoutes() {
    val questWebhookService by inject<QuestWebhookService>()

    route("/settings/quest-webhook") {
        adminOnly {
            get({
                tags = listOf("quest-webhook")
                summary = "クエスト Webhook 設定取得（admin）"
                response {
                    code(HttpStatusCode.OK) {
                        body<QuestWebhookSettings>()
                    }
                }
            }) {
                call.respond(questWebhookService.getSettings())
            }

            put({
                tags = listOf("quest-webhook")
                summary = "クエスト Webhook 設定更新（admin）"
                request {
                    body<QuestWebhookSettings>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<QuestWebhookSettings>()
                    }
                }
            }) {
                val settings = call.receive<QuestWebhookSettings>()
                questWebhookService.updateSettings(settings)
                call.respond(questWebhookService.getSettings())
            }
        }
    }
}
