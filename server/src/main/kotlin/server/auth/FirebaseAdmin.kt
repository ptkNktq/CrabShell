package server.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import java.io.File
import java.io.FileInputStream

object FirebaseAdmin {
    private var initialized = false

    fun initialize() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            initialized = true
            return
        }

        val serviceAccountPath =
            System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH")
                ?: "firebase-service-account.json"

        val file = File(serviceAccountPath)
        if (!file.exists() || !file.isFile) {
            println("WARNING: Firebase service account file not found at '$serviceAccountPath'. Authentication will reject all requests.")
            return
        }

        val options =
            FirebaseOptions.builder()
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
            null
        }
    }

    fun createCustomToken(uid: String): String? {
        if (!initialized) return null
        return try {
            FirebaseAuth.getInstance().createCustomToken(uid)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserByEmail(email: String): com.google.firebase.auth.UserRecord? {
        if (!initialized) return null
        return try {
            FirebaseAuth.getInstance().getUserByEmail(email)
        } catch (e: Exception) {
            null
        }
    }
}
