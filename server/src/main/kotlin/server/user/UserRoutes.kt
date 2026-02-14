package server.user

import com.google.firebase.auth.FirebaseAuth
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.User
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
                        )
                    )
                }
                page = page.nextPage
            }
            call.respond(users)
        }
    }
}
