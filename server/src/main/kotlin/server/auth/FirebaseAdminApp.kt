package server.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val SERVICE_ACCOUNT_PATH = "firebase-service-account.json"

/**
 * Firebase Admin SDK のデフォルトアプリ（[FirebaseApp]）を初期化して保持し、[close] で削除する。
 * Firestore・Firebase Auth はこの [app] から作る。
 *
 * サービスアカウントファイルが無い場合は生成時に例外を投げる。Firestore・Firebase Auth のどちらも
 * このアプリに依存するため、初期化できないまま起動しても API が動作しない。起動時点で失敗させて原因を明示する。
 */
class FirebaseAdminApp(
    serviceAccountFile: File = File(SERVICE_ACCOUNT_PATH),
) : AutoCloseable {
    val app: FirebaseApp = initialize(serviceAccountFile)

    private val closed = AtomicBoolean(false)

    /** [app] を削除する。2 回目以降の呼び出しは何もしない */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            app.delete()
        }
    }

    private companion object {
        /** デフォルトアプリを初期化して返す（初期化済みならそれを返す） */
        fun initialize(serviceAccountFile: File): FirebaseApp {
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
    }
}
