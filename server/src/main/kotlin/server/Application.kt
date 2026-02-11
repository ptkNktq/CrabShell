package server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.DashboardItem
import model.Status
import server.auth.FirebaseAdmin
import server.auth.authenticated

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    FirebaseAdmin.initialize()

    install(ContentNegotiation) { json() }
    install(CORS) {
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        anyHost()
    }

    routing {
        route("/api") {
            authenticated {
                get("/items") {
                    call.respond(sampleItems())
                }
            }
        }

        // Compose Wasm フロントエンドを配信
        staticResources("/", "static") {
            default("index.html")
        }
    }
}

private fun sampleItems(): List<DashboardItem> = listOf(
    DashboardItem(1, "Server Setup", "Ktor server is running", Status.ACTIVE),
    DashboardItem(2, "Frontend", "Compose Web (Wasm)", Status.ACTIVE),
    DashboardItem(3, "Database", "Not yet configured", Status.PENDING),
)
