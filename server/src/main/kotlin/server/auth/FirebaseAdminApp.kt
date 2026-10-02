package server.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import server.util.CloseOnce
import java.io.File

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

    private val closeOnce = CloseOnce()

    /** [app] を削除する */
    override fun close() = closeOnce { app.delete() }

    private companion object {
        /**
         * デフォルトアプリを初期化して返す。
         * このクラスが作ったアプリだけを [close] で削除するため、初期化済みのアプリは使い回さない
         * （DI の single で 1 回だけ生成される。二重に初期化した場合は Firebase が例外を投げる）
         */
        fun initialize(serviceAccountFile: File): FirebaseApp {
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
