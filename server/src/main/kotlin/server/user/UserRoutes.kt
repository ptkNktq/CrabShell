package server.user

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import model.UpdateDisplayNameRequest
import model.User
import org.koin.ktor.ext.inject
import server.auth.FirebaseAuthRepository
import server.auth.adminOnly
import server.auth.authenticated

fun Route.userRoutes() {
    val firebaseAuthRepository by inject<FirebaseAuthRepository>()

    authenticated {
        get("/users", {
            tags = listOf("user")
            summary = "ユーザー一覧取得"
            response {
                code(HttpStatusCode.OK) {
                    body<List<User>>()
                }
            }
        }) {
            call.respond(firebaseAuthRepository.listUsers())
        }
    }

    adminOnly {
        put("/users/{uid}/name", {
            tags = listOf("user")
            summary = "ユーザー表示名更新（admin）"
            request {
                pathParameter<String>("uid") { description = "ユーザー UID" }
                body<UpdateDisplayNameRequest>()
            }
            response {
                code(HttpStatusCode.OK) {
                    body<User>()
                }
            }
        }) {
            val uid = call.parameters.getOrFail("uid")
            val request = call.receive<UpdateDisplayNameRequest>()

            call.respond(firebaseAuthRepository.updateDisplayName(uid, request.displayName))
        }
    }
}
