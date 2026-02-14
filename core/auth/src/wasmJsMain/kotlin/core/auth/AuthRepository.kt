package core.auth

import kotlinx.coroutines.await
import model.User

@OptIn(ExperimentalWasmJsInterop::class)
object AuthRepository {

    private val auth by lazy { firebaseAuth(getFirebase()) }

    fun startListening() {
        onAuthStateChanged(
            auth = auth,
            onUser = { uid: JsString, email: JsString, displayName: JsString ->
                // ユーザーがサインイン中 → IDトークン + Custom Claims を取得して状態更新
                getIdTokenResult(auth).then<Nothing?> { resultJs ->
                    val token = resultJs?.let { getTokenFromResult(it).toString() } ?: ""
                    val isAdmin = resultJs?.let { getIsAdminFromResult(it).toBoolean() } ?: false
                    val user = User(
                        uid = uid.toString(),
                        email = email.toString(),
                        displayName = displayName.toString().ifEmpty { null },
                        isAdmin = isAdmin,
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

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            reauthenticateAndChangePassword(
                auth,
                currentPassword.toJsString(),
                newPassword.toJsString(),
            ).await<Nothing?>()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): String? {
        return try {
            val resultJs = getIdTokenResult(auth).await<JsAny?>()
            val token = resultJs?.let { getTokenFromResult(it).toString() }
            if (token != null) {
                val isAdmin = getIsAdminFromResult(resultJs).toBoolean()
                AuthStateHolder.idToken = token
                // admin 状態も更新
                val currentState = AuthStateHolder.state
                if (currentState is AuthState.Authenticated) {
                    AuthStateHolder.setAuthenticated(
                        currentState.user.copy(isAdmin = isAdmin),
                        token,
                    )
                }
            }
            token
        } catch (e: Throwable) {
            null
        }
    }
}
