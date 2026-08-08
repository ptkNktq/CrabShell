package server

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.openApi
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import server.feeding.FeedingNotificationService
import server.feeding.feedingRoutes
import server.garbage.GarbageNotificationService
import server.garbage.garbageRoutes
import server.loginhistory.loginHistoryRoutes
import server.migration.FirestoreMigrations
import server.money.MoneyDueDateNotificationService
import server.money.moneyDueDateNotificationRoutes
import server.money.moneyRoutes
import server.money.moneyWebhookRoutes
import server.money.paymentWebhookRoutes
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

private val logger = LoggerFactory.getLogger("server.Application")

// Firestore マイグレーションの最大待ち時間。
// Dockerfile の HEALTHCHECK start-period=15s 以下に収める必要があるため、初回実行時間 + 余裕分を確保。
// 大量データで収まらなくなった場合は HEALTHCHECK 側と併せて再調整すること。
private val MIGRATION_TIMEOUT = 60.seconds

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

    // 同期実行: マイグレーション完了前に HTTP リクエストを受け付けないようにする。
    // 失敗時は例外を伝播させてサーバー起動自体を中断し、未移行のまま運用を開始しない。
    // Firestore のネットワーク不通・認証失敗で無期限ブロックしないよう withTimeout で守る。
    val firestoreMigrations by inject<FirestoreMigrations>()
    runBlocking {
        withTimeout(MIGRATION_TIMEOUT) { firestoreMigrations.runAll() }
    }

    val petRepository by inject<PetRepository>()
    petRepository.seedDefaultPet()

    val feedingNotificationService by inject<FeedingNotificationService>()
    launch { feedingNotificationService.runPollingLoop() }

    val garbageNotificationService by inject<GarbageNotificationService>()
    launch { garbageNotificationService.runPollingLoop() }

    val moneyDueDateNotificationService by inject<MoneyDueDateNotificationService>()
    launch { moneyDueDateNotificationService.runPollingLoop() }

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
        // ログイン API（パスキー 5/分は IP 単位、メール/パスワードは Firebase 側制御）と
        // 同等以上の余裕を持たせて 10/分。GET/POST 共通バケットのため、ログイン直後に
        // 設定画面を開くと 2 消費する点に注意。
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
        // 不正な JSON（enum の未知値、型不一致等）は 400 で返す（Ktor デフォルトの 500 を上書き）。
        // cause.message には内部型名・フィールド名を含みうるため、クライアントには固定メッセージを返し
        // 詳細はサーバーログのみに出す。
        exception<SerializationException> { call, cause ->
            logger.warn("Invalid request body on ${call.request.path()}: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
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
            get("rapidoc") {
                call.respondText(RAPIDOC_HTML, ContentType.Text.Html)
            }
        }

        route("/api") {
            firebaseConfigRoute()
            userRoutes()
            petRoutes()
            feedingRoutes()
            garbageRoutes()
            moneyRoutes()
            moneyWebhookRoutes()
            moneyDueDateNotificationRoutes()
            paymentWebhookRoutes()
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

/**
 * RapiDoc 単一 HTML。`/rapidoc` で配信し、CrabShell の `/api.json`（OpenAPI spec）を読み込む。
 *
 * - RapiDoc 本体は `server/src/main/resources/static/vendor/rapidoc-<version>/` に同梱
 *   （CDN 依存なし、エアギャップ環境・ネットワーク断時も動作）。バージョン bump 手順:
 *   1. `curl -sL https://unpkg.com/rapidoc@<new-ver>/dist/rapidoc-min.js -o static/vendor/rapidoc-<new-ver>/rapidoc-min.js`
 *   2. `curl -sL https://unpkg.com/rapidoc@<new-ver>/dist/rapidoc-min.js.LICENSE.txt -o static/vendor/rapidoc-<new-ver>/rapidoc-min.js.LICENSE.txt`
 *   3. 旧 `static/vendor/rapidoc-<old-ver>/` を削除
 *   4. 本 HTML の script src パスを新バージョンに更新
 * - 同梱ファイルにはバナーコメント `/*! RapiDoc <ver> | Author - ... | License ... */` が
 *   保持されており、隣接の `*.LICENSE.txt` に bundled 依存（buffer, js-yaml 他）の
 *   MIT/BSD 通知が含まれる。MIT 再配布義務はこれで遵守。
 * - primary-color はプロジェクトのブランドカラー (#E8844A) に揃える。
 * - dark テーマ既定。light/dark の切替トグルはヘッダ右上の UI から可能。
 * - allow-spec-url-load 等の入力 UI は本プロジェクトには不要なので無効化。
 * - 英語 UI なので html lang="en"（lang="ja" だとスクリーンリーダーが日本語アクセントで読み上げる）。
 */
private val RAPIDOC_HTML =
    """
    <!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8">
        <title>CrabShell API</title>
        <script type="module" src="/vendor/rapidoc-9.3.8/rapidoc-min.js"></script>
        <style>html, body { margin: 0; padding: 0; height: 100%; }</style>
      </head>
      <body>
        <rapi-doc
          spec-url="/api.json"
          theme="dark"
          render-style="read"
          schema-style="table"
          show-header="true"
          allow-server-selection="false"
          allow-spec-url-load="false"
          allow-spec-file-load="false"
          allow-spec-file-download="false"
          allow-authentication="true"
          allow-try="true"
          primary-color="#E8844A"
          font-size="large"
        ></rapi-doc>
      </body>
    </html>
    """.trimIndent()
