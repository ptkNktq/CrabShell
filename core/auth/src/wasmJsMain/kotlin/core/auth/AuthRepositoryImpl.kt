package core.auth

import core.common.AppLogger
import kotlinx.coroutines.await
import model.User

private const val TAG = "Auth"

@OptIn(ExperimentalWasmJsInterop::class)
class AuthRepositoryImpl(
    private val authStateHolder: AuthStateHolder,
) : AuthRepository {
    private val auth by lazy { firebaseAuth(getFirebase()) }

    override fun startListening() {
        AppLogger.d(TAG, "Starting auth state listener")
        onAuthStateChanged(
            auth = auth,
            onUser = { uid: JsString, email: JsString, displayName: JsString ->
                // ユーザーがサインイン中 → IDトークン + Custom Claims を取得して状態更新
                getIdTokenResult(auth).then<Nothing?> { resultJs ->
                    val token = resultJs?.let { getTokenFromResult(it).toString() } ?: ""
                    val isAdmin = resultJs?.let { getIsAdminFromResult(it).toBoolean() } ?: false
                    val user =
                        User(
                            uid = uid.toString(),
                            email = email.toString(),
                            displayName = displayName.toString().ifEmpty { null },
                            isAdmin = isAdmin,
                        )
                    AppLogger.i(TAG, "Authenticated: ${user.email} (admin=$isAdmin)")
                    authStateHolder.setAuthenticated(user, token)
                    null
                }
            },
            onNull = {
                AppLogger.i(TAG, "Unauthenticated")
                authStateHolder.setUnauthenticated()
            },
        )
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit> =
        try {
            AppLogger.d(TAG, "Signing in: $email")
            signInWithEmailAndPassword(auth, email.toJsString(), password.toJsString()).await<Nothing?>()
            Result.success(Unit)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Sign-in failed: ${e.message}")
            Result.failure(e)
        }

    override suspend fun signOut(): Result<Unit> =
        try {
            AppLogger.d(TAG, "Signing out")
            firebaseSignOut(auth).await<Nothing?>()
            Result.success(Unit)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Sign-out failed: ${e.message}")
            Result.failure(e)
        }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<Unit> =
        try {
            AppLogger.d(TAG, "Changing password")
            reauthenticateAndChangePassword(
                auth,
                currentPassword.toJsString(),
                newPassword.toJsString(),
            ).await<Nothing?>()
            AppLogger.i(TAG, "Password changed successfully")
            Result.success(Unit)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Change password failed: ${e.message}")
            Result.failure(e)
        }

    override suspend fun signInWithCustomToken(token: String): Result<Unit> =
        try {
            AppLogger.d(TAG, "Signing in with custom token (passkey)")
            signInWithCustomToken(auth, token.toJsString()).await<Nothing?>()
            Result.success(Unit)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Custom token sign-in failed: ${e.message}")
            Result.failure(e)
        }

    override fun isWebAuthnSupported(): Boolean = isWebAuthnAvailable()

    override suspend fun refreshToken(): String? =
        try {
            AppLogger.d(TAG, "Refreshing token")
            val resultJs = forceRefreshIdToken(auth).await<JsAny?>()
            val token = resultJs?.let { getTokenFromResult(it).toString() }
            if (token != null) {
                val isAdmin = getIsAdminFromResult(resultJs).toBoolean()
                val currentState = authStateHolder.state
                if (currentState is AuthState.Authenticated) {
                    authStateHolder.setAuthenticated(
                        currentState.user.copy(isAdmin = isAdmin),
                        token,
                    )
                } else {
                    authStateHolder.idToken = token
                }
                AppLogger.d(TAG, "Token refreshed")
            }
            token
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Token refresh failed: ${e.message}")
            null
        }
}
