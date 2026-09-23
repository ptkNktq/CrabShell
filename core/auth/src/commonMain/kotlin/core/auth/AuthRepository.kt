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

    /**
     * 現在のユーザーの ID トークンを取得する。
     *
     * [forceRefresh] が false の場合、有効期限まで 5 分以上あるトークンはキャッシュを返し、
     * 期限切れ・期限間近であれば Firebase が更新してから返す。API リクエストごとに呼び出すこと。
     *
     * @see <a href="https://firebase.google.com/docs/reference/js/auth.user.md#usergetidtoken">User.getIdToken</a>
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): IdTokenResult

    /**
     * ID トークンを強制更新し、Custom Claims（isAdmin 等）の変更を認証状態に反映する。
     * バックグラウンドタブ復帰時など、権限の変更を取り込みたいタイミングで呼び出す。
     * 失敗しても認証状態は変更しない。
     */
    suspend fun refreshClaims()

    suspend fun signInWithCustomToken(token: String): Result<Unit>

    fun isWebAuthnSupported(): Boolean
}
