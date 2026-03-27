package server.config

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val REQUIRED_KEYS =
    listOf(
        "FIREBASE_API_KEY",
        "FIREBASE_AUTH_DOMAIN",
        "FIREBASE_PROJECT_ID",
        "FIREBASE_STORAGE_BUCKET",
        "FIREBASE_MESSAGING_SENDER_ID",
        "FIREBASE_APP_ID",
    )

private fun escapeJs(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

fun Route.firebaseConfigRoute() {
    get("/firebase-config.js") {
        val config = REQUIRED_KEYS.associateWith { EnvConfig[it] }
        val missing = config.filterValues { it == null }.keys
        if (missing.isNotEmpty()) {
            call.respond(HttpStatusCode.InternalServerError, "Missing Firebase config: ${missing.joinToString()}")
            return@get
        }

        val js =
            """
            var firebaseConfig = {
                apiKey: "${escapeJs(config["FIREBASE_API_KEY"]!!)}",
                authDomain: "${escapeJs(config["FIREBASE_AUTH_DOMAIN"]!!)}",
                projectId: "${escapeJs(config["FIREBASE_PROJECT_ID"]!!)}",
                storageBucket: "${escapeJs(config["FIREBASE_STORAGE_BUCKET"]!!)}",
                messagingSenderId: "${escapeJs(config["FIREBASE_MESSAGING_SENDER_ID"]!!)}",
                appId: "${escapeJs(config["FIREBASE_APP_ID"]!!)}",
            };
            firebase.initializeApp(firebaseConfig);
            """.trimIndent()

        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respondText(js, ContentType("application", "javascript"))
    }
}
