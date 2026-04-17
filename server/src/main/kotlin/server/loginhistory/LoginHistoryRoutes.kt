package server.loginhistory

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.LoginEvent
import model.RecordLoginRequest
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.ratelimit.RateLimitNames
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val TTL_DAYS = 90L
private val ALLOWED_LOGIN_METHODS = setOf("email", "passkey")

fun Route.loginHistoryRoutes() {
    val loginHistoryRepository by inject<LoginHistoryRepository>()

    authenticated {
        route("/login-history") {
            rateLimit(RateLimitNames.LOGIN_HISTORY) {
                post({
                    tags = listOf("login-history")
                    summary = "ログインイベント記録"
                    request {
                        body<RecordLoginRequest>()
                    }
                    response {
                        code(HttpStatusCode.Created) {}
                    }
                }) {
                    val uid = call.firebasePrincipal.uid
                    val body = call.receive<RecordLoginRequest>()
                    if (body.loginMethod !in ALLOWED_LOGIN_METHODS) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid login method"))
                        return@post
                    }
                    val now = Instant.now()

                    val event =
                        LoginEvent(
                            id = UUID.randomUUID().toString(),
                            ipAddress = call.request.origin.remoteAddress,
                            userAgent = call.request.headers["User-Agent"],
                            loginMethod = body.loginMethod,
                        )
                    val expireAt = now.plus(TTL_DAYS, ChronoUnit.DAYS)

                    loginHistoryRepository.recordLogin(uid, event, now, expireAt)
                    call.respond(HttpStatusCode.Created)
                }
            }

            get({
                tags = listOf("login-history")
                summary = "ログイン履歴取得"
                request {
                    queryParameter<Int>("limit") {
                        description = "取得件数（デフォルト: 5、最大: 50）"
                        required = false
                    }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<List<LoginEvent>>()
                    }
                }
            }) {
                val uid = call.firebasePrincipal.uid
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 5).coerceIn(1, 50)
                val history = loginHistoryRepository.getHistory(uid, limit)
                call.respond(history)
            }
        }
    }
}
