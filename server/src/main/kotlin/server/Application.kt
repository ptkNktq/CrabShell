package server

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import server.auth.FirebaseAdmin
import server.auth.configureAuth
import server.auth.firebasePrincipal
import server.cache.cacheRoutes
import server.config.EnvConfig
import server.config.firebaseConfigRoute
import server.di.serverModule
import server.feeding.FeedingReminderService
import server.feeding.feedingRoutes
import server.garbage.GarbageNotificationService
import server.garbage.garbageRoutes
import server.loginhistory.loginHistoryRoutes
import server.money.moneyRoutes
import server.passkey.PasskeyDatabase
import server.passkey.passkeyRoutes
import server.pet.PetAccessDeniedException
import server.pet.PetRepository
import server.pet.petRoutes
import server.quest.pointRoutes
import server.quest.questRoutes
import server.quest.questWebhookRoutes
import server.ratelimit.RateLimitNames
import server.report.reportRoutes
import server.user.userRoutes
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // dotenv-java の値を Logback に反映（logback.xml は OS 環境変数のみ参照するため）
    EnvConfig["LOG_LEVEL"]?.let { level ->
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.level =
            ch.qos.logback.classic.Level
                .valueOf(level)
    }

    FirebaseAdmin.initialize()
    PasskeyDatabase.initialize()

    install(Koin) { modules(serverModule) }

    val petRepository by inject<PetRepository>()
    petRepository.seedDefaultPet()

    val feedingReminderService by inject<FeedingReminderService>()
    launch { feedingReminderService.runPollingLoop() }

    val garbageNotificationService by inject<GarbageNotificationService>()
    launch { garbageNotificationService.runPollingLoop() }

    configureAuth()
    install(CallLogging) {
        level = Level.DEBUG
        filter { call -> call.request.path().startsWith("/api") }
    }
    install(ContentNegotiation) { json() }
    install(RequestBodyLimit) { bodyLimit { 256_000L } }

    // リバースプロキシ背後で正しいクライアント IP を取得
    // リバースプロキシ側で X-Forwarded-For を上書き（クライアント送信値を破棄）していることが前提
    install(XForwardedHeaders)

    install(RateLimit) {
        // 未認証エンドポイント: IP ベース
        register(RateLimitNames.PASSKEY_AUTH) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        // 認証済みエンドポイント: UID ベース
        register(RateLimitNames.AI_GENERATE) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
            requestKey { call -> call.firebasePrincipal.uid }
        }
        register(RateLimitNames.LOGIN_HISTORY) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.firebasePrincipal.uid }
        }
    }

    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, mapOf("error" to "Too many requests"))
        }
        exception<PetAccessDeniedException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not a member of this pet"))
        }
        exception<MissingRequestParameterException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "${cause.parameterName} is required"))
        }
        exception<ParameterConversionException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ${cause.parameterName}: ${cause.type}"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        // 不正な JSON（enum の未知値、型不一致等）は 400 で返す（Ktor デフォルトの 500 を上書き）
        exception<SerializationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid request body")))
        }
    }

    install(OpenApi) {
        pathFilter = { _, url -> url.firstOrNull() == "api" }
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
            firebaseConfigRoute()
            userRoutes()
            petRoutes()
            feedingRoutes()
            garbageRoutes()
            moneyRoutes()
            reportRoutes()
            questRoutes()
            pointRoutes()
            questWebhookRoutes()
            cacheRoutes()
            loginHistoryRoutes()
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
                        path.endsWith("app.js") ->
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
