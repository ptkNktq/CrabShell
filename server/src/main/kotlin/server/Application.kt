package server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import shared.model.DashboardItem
import shared.model.Status

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CORS) {
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        anyHost()
    }

    routing {
        route("/api") {
            get("/items") {
                call.respond(sampleItems())
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
