package server.config

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.firebaseConfigRoute() {
    get("/firebase-config.js") {
        val apiKey = EnvConfig["FIREBASE_API_KEY"]
        val authDomain = EnvConfig["FIREBASE_AUTH_DOMAIN"]
        val projectId = EnvConfig["FIREBASE_PROJECT_ID"]
        val storageBucket = EnvConfig["FIREBASE_STORAGE_BUCKET"]
        val messagingSenderId = EnvConfig["FIREBASE_MESSAGING_SENDER_ID"]
        val appId = EnvConfig["FIREBASE_APP_ID"]

        if (apiKey == null || projectId == null) {
            call.respond(HttpStatusCode.InternalServerError, "Firebase config is not set")
            return@get
        }

        val js =
            """
            var firebaseConfig = {
                apiKey: "$apiKey",
                authDomain: "$authDomain",
                projectId: "$projectId",
                storageBucket: "$storageBucket",
                messagingSenderId: "$messagingSenderId",
                appId: "$appId",
            };
            firebase.initializeApp(firebaseConfig);
            """.trimIndent()

        call.respondText(js, ContentType("application", "javascript"))
    }
}
