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
         * - `auth/user-token-expired` / `auth/invalid-user-token` / `auth/user-disabled` / `auth/user-not-found`:
         *   AuthErrorCodes に定義されたコード。なお JS SDK はサーバーの `USER_NOT_FOUND` を
         *   `auth/user-token-expired` に変換するため、トークン更新で `auth/user-not-found` が返ることは通常ない
         * - `auth/invalid-refresh-token` / `auth/invalid-grant-type` / `auth/missing-refresh-token` /
         *   `auth/project-number-mismatch`: トークン交換 API のエラー（`INVALID_REFRESH_TOKEN` 等）。
         *   AuthErrorCodes に無いサーバーエラーは JS SDK が小文字・ハイフン区切りに変換して返す。
         *   SDK はこれらでは自動サインアウトしないため、ここで拾わないと更新失敗が続いて復帰できない
         *
         * @see <a href="https://firebase.google.com/docs/reference/js/auth.md#autherrorcodes">AuthErrorCodes</a>
         * @see <a href="https://firebase.google.com/docs/reference/rest/auth">Firebase Auth REST API（refresh token 交換のエラー）</a>
         * @see <a href="https://github.com/firebase/firebase-js-sdk/blob/main/packages/auth/src/api/index.ts">JS SDK のサーバーエラー変換</a>
         */
        private val SESSION_INVALID_CODES =
            setOf(
                "auth/user-token-expired",
                "auth/invalid-user-token",
                "auth/user-disabled",
                "auth/user-not-found",
                "auth/invalid-refresh-token",
                "auth/invalid-grant-type",
                "auth/missing-refresh-token",
                "auth/project-number-mismatch",
            )

        /** Firebase Auth のエラーコードから失敗の種類を判定する。 */
        fun failureOf(code: String): IdTokenResult = if (code in SESSION_INVALID_CODES) SessionInvalid(code) else TransientFailure(code)
    }
}
