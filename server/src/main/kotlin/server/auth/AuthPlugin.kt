package server.auth

import com.google.firebase.auth.FirebaseToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

val FirebaseTokenKey = AttributeKey<FirebaseToken>("FirebaseToken")

val FirebaseAuthPlugin = createRouteScopedPlugin("FirebaseAuth") {
    onCall { call ->
        val authHeader = call.request.header(HttpHeaders.Authorization)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid Authorization header"))
            return@onCall
        }

        val token = authHeader.removePrefix("Bearer ")
        val firebaseToken = FirebaseAdmin.verifyIdToken(token)
        if (firebaseToken == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            return@onCall
        }

        // 検証済みトークンをリクエスト属性に保存
        call.attributes.put(FirebaseTokenKey, firebaseToken)
    }
}

fun Route.authenticated(build: Route.() -> Unit): Route {
    val authenticatedRoute = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Transparent
    })

    authenticatedRoute.install(FirebaseAuthPlugin)
    authenticatedRoute.build()
    return authenticatedRoute
}
