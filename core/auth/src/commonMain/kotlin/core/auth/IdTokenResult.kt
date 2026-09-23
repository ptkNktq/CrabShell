package core.auth

/**
 * ID トークン取得（[AuthRepository.getIdToken]）の結果。
 *
 * 取得失敗を「セッション自体が無効（再ログインが必要）」と「一時的な失敗（ネットワーク等）」に分ける。
 * 一時的な失敗でサインアウトすると、スリープ明けやバックグラウンドタブ復帰直後の通信断だけで
 * 強制ログアウトされてしまうため、呼び出し側はこの 2 つを区別して扱う。
 */
sealed interface IdTokenResult {
    data class Success(
        val token: String,
    ) : IdTokenResult

    /** サインインしていない（currentUser が存在しない）。 */
    data object SignedOut : IdTokenResult

    /** セッションが無効になっており、再ログインが必要。 */
    data class SessionInvalid(
        val code: String,
    ) : IdTokenResult

    /** ネットワーク断などの一時的な失敗。セッション自体は有効な可能性がある。 */
    data class TransientFailure(
        val code: String,
    ) : IdTokenResult

    companion object {
        /**
         * セッションが無効であることを示す Firebase Auth のエラーコード。
         * これ以外（`auth/network-request-failed` や `auth/internal-error` 等）は一時的な失敗として扱う。
         *
         * @see <a href="https://firebase.google.com/docs/reference/js/auth.md#autherrorcodes">AuthErrorCodes</a>
         */
        private val SESSION_INVALID_CODES =
            setOf(
                "auth/user-token-expired",
                "auth/invalid-user-token",
                "auth/user-disabled",
                "auth/user-not-found",
            )

        /** Firebase Auth のエラーコードから失敗の種類を判定する。 */
        fun failureOf(code: String): IdTokenResult = if (code in SESSION_INVALID_CODES) SessionInvalid(code) else TransientFailure(code)
    }
}
