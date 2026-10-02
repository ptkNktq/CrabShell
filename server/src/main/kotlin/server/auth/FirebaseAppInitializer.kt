package server.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File

private const val SERVICE_ACCOUNT_PATH = "firebase-service-account.json"

/**
 * Firebase Admin SDK のデフォルトアプリを初期化して返す（初期化済みならそれを返す）。
 *
 * サービスアカウントファイルが無い場合は例外を投げる。Firestore・Firebase Auth のどちらもこのアプリに
 * 依存するため、初期化できないまま起動しても API が動作しない。起動時点で失敗させて原因を明示する。
 */
fun initializeFirebaseApp(serviceAccountFile: File = File(SERVICE_ACCOUNT_PATH)): FirebaseApp {
    FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }

    check(serviceAccountFile.isFile) {
        "Firebase service account file not found at '${serviceAccountFile.absolutePath}'"
    }
    val options =
        serviceAccountFile.inputStream().use { stream ->
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()
        }
    return FirebaseApp.initializeApp(options)
}
