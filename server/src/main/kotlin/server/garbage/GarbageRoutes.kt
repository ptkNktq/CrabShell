package server.garbage

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.GarbageNotificationSettings
import model.GarbageTypeSchedule
import org.koin.ktor.ext.inject
import server.auth.adminOnly
import server.auth.authenticated

fun Route.garbageRoutes() {
    val garbageRepository by inject<GarbageRepository>()

    route("/garbage/schedule") {
        authenticated {
            get({
                tags = listOf("garbage")
                summary = "ゴミ出しスケジュール取得"
                response {
                    code(HttpStatusCode.OK) {
                        body<List<GarbageTypeSchedule>>()
                    }
                }
            }) {
                call.respond(garbageRepository.getSchedules())
            }
        }

        adminOnly {
            put({
                tags = listOf("garbage")
                summary = "ゴミ出しスケジュール更新（admin）"
                request {
                    body<List<GarbageTypeSchedule>>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<List<GarbageTypeSchedule>>()
                    }
                }
            }) {
                val schedules = call.receive<List<GarbageTypeSchedule>>()
                garbageRepository.saveSchedules(schedules)
                call.respond(schedules)
            }
        }
    }

    route("/garbage/notification-settings") {
        authenticated {
            get({
                tags = listOf("garbage")
                summary = "ゴミ出し通知設定取得"
                response {
                    code(HttpStatusCode.OK) {
                        body<GarbageNotificationSettings>()
                    }
                }
            }) {
                call.respond(garbageRepository.getNotificationSettings())
            }
        }

        adminOnly {
            put({
                tags = listOf("garbage")
                summary = "ゴミ出し通知設定更新（admin）"
                request {
                    body<GarbageNotificationSettings>()
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<GarbageNotificationSettings>()
                    }
                }
            }) {
                val settings = call.receive<GarbageNotificationSettings>()
                val timeRegex = Regex("""\d{2}:\d{2}""")
                if (!timeRegex.matches(settings.notifyTime)) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid notifyTime format"))
                    return@put
                }
                val (h, m) = settings.notifyTime.split(":").map { it.toInt() }
                if (h !in 0..23 || m !in 0..59) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid notifyTime value"))
                    return@put
                }
                garbageRepository.saveNotificationSettings(settings)
                call.respond(settings)
            }
        }
    }
}
