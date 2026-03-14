package server.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import java.io.File
import java.io.FileInputStream

private val logger = LoggerFactory.getLogger("FirebaseAdmin")

object FirebaseAdmin {
    private var initialized = false

    fun initialize() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            initialized = true
            return
        }

        val serviceAccountPath =
            EnvConfig["FIREBASE_SERVICE_ACCOUNT_PATH"]
                ?: "firebase-service-account.json"

        val file = File(serviceAccountPath)
        if (!file.exists() || !file.isFile) {
            logger.warn("Firebase service account file not found at '{}'. Authentication will reject all requests.", serviceAccountPath)
            return
        }

        val options =
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(file)))
                .build()

        FirebaseApp.initializeApp(options)
        initialized = true
    }

    fun verifyIdToken(idToken: String): FirebaseToken? {
        if (!initialized) return null
        return try {
            FirebaseAuth.getInstance().verifyIdToken(idToken)
        } catch (e: Exception) {
            logger.warn("Firebase token verification failed", e)
            null
        }
    }

    fun createCustomToken(uid: String): String? {
        if (!initialized) return null
        return try {
            FirebaseAuth.getInstance().createCustomToken(uid)
        } catch (e: Exception) {
            logger.warn("Failed to create custom token for uid={}", uid, e)
            null
        }
    }

    fun getUserByEmail(email: String): com.google.firebase.auth.UserRecord? {
        if (!initialized) return null
        return try {
            FirebaseAuth.getInstance().getUserByEmail(email)
        } catch (e: Exception) {
            logger.warn("Failed to get user by email", e)
            null
        }
    }
}
