package core.auth

interface AuthRepository {
    fun startListening()

    suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<Unit>

    suspend fun refreshToken(): String?

    suspend fun signInWithCustomToken(token: String): Result<Unit>

    fun isWebAuthnSupported(): Boolean
}
