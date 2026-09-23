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
                // ユーザーがサインイン中 → Custom Claims（isAdmin）を取得して状態更新
                getIdTokenResult(auth).then<Nothing?> { resultJs ->
                    val isAdmin = resultJs?.let { getIsAdminFromResult(it).toBoolean() } ?: false
                    val user =
                        User(
                            uid = uid.toString(),
                            email = email.toString(),
                            displayName = displayName.toString().ifEmpty { null },
                            isAdmin = isAdmin,
                        )
                    AppLogger.i(TAG, "Authenticated: ${user.email} (admin=$isAdmin)")
                    authStateHolder.setAuthenticated(user)
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

    override suspend fun getIdToken(forceRefresh: Boolean): IdTokenResult {
        // getIdTokenOrError は reject せず、失敗時は Firebase のエラーコードを返す（JS 側で変換済み）
        val resultJs = getIdTokenOrError(auth, forceRefresh).await<JsAny?>() ?: return IdTokenResult.SignedOut
        val errorCode = getErrorCodeFromResult(resultJs)?.toString()
        if (errorCode != null) {
            AppLogger.w(TAG, "Failed to get ID token (forceRefresh=$forceRefresh): $errorCode")
            return IdTokenResult.failureOf(errorCode)
        }
        return IdTokenResult.Success(getTokenFromResult(resultJs).toString())
    }

    override suspend fun refreshClaims() {
        try {
            AppLogger.d(TAG, "Refreshing claims")
            val resultJs = forceRefreshIdToken(auth).await<JsAny?>() ?: return
            val isAdmin = getIsAdminFromResult(resultJs).toBoolean()
            val currentState = authStateHolder.state
            if (currentState is AuthState.Authenticated) {
                authStateHolder.setAuthenticated(currentState.user.copy(isAdmin = isAdmin))
            }
        } catch (e: Throwable) {
            AppLogger.w(TAG, "Claims refresh failed: ${e.message}")
        }
    }
}
