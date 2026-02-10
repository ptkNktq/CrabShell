package ui.features.auth

import kotlinx.coroutines.await
import shared.model.User

object AuthRepository {

    private val auth by lazy { firebaseAuth(getFirebase()) }

    fun startListening() {
        onAuthStateChanged(
            auth = auth,
            onUser = { uid: JsString, email: JsString, displayName: JsString ->
                // ユーザーがサインイン中 → IDトークンを取得して状態更新
                getIdToken(auth).then<Nothing?> { tokenJs ->
                    val token = tokenJs?.toString() ?: ""
                    val user = User(
                        uid = uid.toString(),
                        email = email.toString(),
                        displayName = displayName.toString().ifEmpty { null },
                    )
                    AuthStateHolder.setAuthenticated(user, token)
                    null
                }
            },
            onNull = {
                AuthStateHolder.setUnauthenticated()
            },
        )
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            signInWithEmailAndPassword(auth, email.toJsString(), password.toJsString()).await<Nothing?>()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            firebaseSignOut(auth).await<Nothing?>()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): String? {
        return try {
            val tokenJs = getIdToken(auth).await<Nothing?>()
            val token = tokenJs?.toString()
            if (token != null) {
                AuthStateHolder.idToken = token
            }
            token
        } catch (e: Throwable) {
            null
        }
    }
}
