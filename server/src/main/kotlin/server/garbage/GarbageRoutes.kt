package server.garbage

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
}
