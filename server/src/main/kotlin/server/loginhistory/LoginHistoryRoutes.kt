package server.loginhistory

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.plugins.BadRequestException
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
import server.geo.IpGeolocationService
import server.ratelimit.RateLimitNames
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val TTL_DAYS = 90L
private const val DEFAULT_LIMIT = 5
private const val MAX_LIMIT = 50

fun Route.loginHistoryRoutes() {
    val loginHistoryRepository by inject<LoginHistoryRepository>()
    val geolocation by inject<IpGeolocationService>()

    authenticated {
        route("/login-history") {
            // POST/GET ともに Firestore アクセスが発生するため、UID 単位でレートリミットを適用する。
            // GET は LoginHistoryViewModel.init から呼ばれるほか再取得も可能で、limit=50 連打で
            // 読み取り課金を膨らませうるため GET も保護対象。
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
                    val now = Instant.now()
                    val ipAddress = call.request.origin.remoteAddress
                    val geo = geolocation.lookup(ipAddress)

                    val input =
                        RecordLoginInput(
                            docId = UUID.randomUUID().toString(),
                            timestamp = now,
                            expireAt = now.plus(TTL_DAYS, ChronoUnit.DAYS),
                            ipAddress = ipAddress,
                            userAgent = call.request.headers["User-Agent"],
                            loginMethod = body.loginMethod,
                            country = geo?.country,
                            region = geo?.region,
                            city = geo?.city,
                        )

                    loginHistoryRepository.recordLogin(uid, input)
                    call.respond(HttpStatusCode.Created)
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
                    val limitParam = call.request.queryParameters["limit"]
                    val limit =
                        if (limitParam == null) {
                            DEFAULT_LIMIT
                        } else {
                            val parsed =
                                limitParam.toIntOrNull()
                                    ?: throw BadRequestException("limit must be an integer between 1 and $MAX_LIMIT")
                            if (parsed !in 1..MAX_LIMIT) {
                                throw BadRequestException("limit must be between 1 and $MAX_LIMIT")
                            }
                            parsed
                        }
                    val history = loginHistoryRepository.getHistory(uid, limit)
                    call.respond(history)
                }
            }
        }
    }
}
