package server

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.DashboardItem
import model.Status
import server.auth.FirebaseAdmin
import server.auth.authenticated
import server.config.EnvConfig
import server.feeding.feedingRoutes
import server.garbage.garbageRoutes
import server.money.moneyRoutes
import server.passkey.PasskeyDatabase
import server.passkey.passkeyRoutes
import server.pet.petRoutes
import server.pet.seedDefaultPet
import server.quest.pointRoutes
import server.quest.questRoutes
import server.quest.webhookRoutes
import server.report.reportRoutes
import server.user.userRoutes

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    FirebaseAdmin.initialize()
    PasskeyDatabase.initialize()
    seedDefaultPet()

    install(ContentNegotiation) { json() }

    install(OpenApi) {
        info {
            title = "CrabShell API"
            version = "1.0.0"
            description = "CrabShell ダッシュボードアプリケーションの API"
        }
        security {
            securityScheme("firebase") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "Firebase ID Token"
            }
            defaultSecuritySchemeNames("firebase")
            defaultUnauthorizedResponse {
                description = "認証エラー"
            }
        }
    }

    val swaggerEnabled = EnvConfig["SWAGGER_ENABLED"]?.toBooleanStrictOrNull() == true

    routing {
        if (swaggerEnabled) {
            route("api.json") { openApi() }
            route("swagger") { swaggerUI("/api.json") }
        }

        route("/api") {
            authenticated {
                get("/items", {
                    tags = listOf("dashboard")
                    summary = "ダッシュボード項目一覧"
                    response {
                        code(HttpStatusCode.OK) {
                            body<List<DashboardItem>>()
                        }
                    }
                }) {
                    call.respond(sampleItems())
                }
            }
            userRoutes()
            petRoutes()
            feedingRoutes()
            garbageRoutes()
            moneyRoutes()
            reportRoutes()
            questRoutes()
            pointRoutes()
            webhookRoutes()
            passkeyRoutes()
        }

        // Compose Wasm フロントエンドを配信
        staticResources("/", "static") {
            default("index.html")
            cacheControl { url ->
                val path = url.path
                when {
                    // エントリーポイント: 毎回サーバーに再検証（ETag/304）
                    path.endsWith("index.html") ||
                        path.endsWith("app.js") ||
                        path.endsWith("firebase-config.js") ->
                        listOf(CacheControl.NoCache(null))
                    // ハッシュ付きファイル（チャンク JS, WASM, フォント等）: 1年キャッシュ
                    else ->
                        listOf(
                            CacheControl.MaxAge(
                                maxAgeSeconds = 31536000,
                                mustRevalidate = false,
                                visibility = CacheControl.Visibility.Public,
                            ),
                        )
                }
            }
        }
    }
}

private fun sampleItems(): List<DashboardItem> =
    listOf(
        DashboardItem(1, "Server Setup", "Ktor server is running", Status.ACTIVE),
        DashboardItem(2, "Frontend", "Compose Web (Wasm)", Status.ACTIVE),
        DashboardItem(3, "Database", "Not yet configured", Status.PENDING),
    )
