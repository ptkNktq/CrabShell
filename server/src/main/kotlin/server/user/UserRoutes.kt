package server.user

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserRecord.UpdateRequest
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.UpdateDisplayNameRequest
import model.User
import server.auth.adminOnly
import server.auth.authenticated

fun Route.userRoutes() {
    authenticated {
        get("/users") {
            val users = mutableListOf<User>()
            var page = FirebaseAuth.getInstance().listUsers(null)
            while (page != null) {
                for (record in page.values) {
                    val isAdmin = record.customClaims["admin"] == true
                    users.add(
                        User(
                            uid = record.uid,
                            email = record.email ?: "",
                            displayName = record.displayName,
                            isAdmin = isAdmin,
                        ),
                    )
                }
                page = page.nextPage
            }
            call.respond(users)
        }
    }

    adminOnly {
        put("/users/{uid}/name") {
            val uid = call.parameters["uid"]!!
            val request = call.receive<UpdateDisplayNameRequest>()

            FirebaseAuth.getInstance().updateUser(
                UpdateRequest(uid).setDisplayName(request.displayName),
            )

            val record = FirebaseAuth.getInstance().getUser(uid)
            val isAdmin = record.customClaims["admin"] == true
            call.respond(
                User(
                    uid = record.uid,
                    email = record.email ?: "",
                    displayName = record.displayName,
                    isAdmin = isAdmin,
                ),
            )
        }
    }
}
