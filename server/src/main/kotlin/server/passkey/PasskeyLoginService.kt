package server.passkey

import org.slf4j.LoggerFactory
import server.auth.FirebaseUserDirectory
import server.auth.FirebaseUserStatus

sealed interface PasskeyLoginResult {
    data class Success(
        val customToken: String,
    ) : PasskeyLoginResult

    /** パスキーの所有者が Firebase Auth 上に存在しない、または無効化されている */
    data object Rejected : PasskeyLoginResult

    /** Firebase の障害等でユーザー状態の確認・トークン発行ができない */
    data object Unavailable : PasskeyLoginResult
}

/**
 * パスキー認証（署名検証）に成功したクレデンシャルの所有者に対して、カスタムトークンを発行する。
 *
 * 存在しない uid のカスタムトークンでサインインすると Firebase Auth にユーザーが新規作成されるため、
 * 発行前に所有者が実在し有効であることを必ず確認する。
 */
class PasskeyLoginService(
    private val userDirectory: FirebaseUserDirectory,
    private val credentialStore: PasskeyCredentialStore,
) {
    private val logger = LoggerFactory.getLogger(PasskeyLoginService::class.java)

    fun issueCustomToken(firebaseUid: String): PasskeyLoginResult {
        val status =
            try {
                userDirectory.getUserStatus(firebaseUid)
            } catch (e: Exception) {
                logger.warn("Failed to get user status for uid={}", firebaseUid, e)
                return PasskeyLoginResult.Unavailable
            }

        return when (status) {
            FirebaseUserStatus.NOT_FOUND -> {
                // 削除済みユーザーのパスキーは二度と使えないため、ログイン時に検出して削除する
                val deleted = credentialStore.deleteCredentials(firebaseUid)
                logger.warn("Rejected passkey login for non-existent uid={}; deleted {} credential(s)", firebaseUid, deleted)
                PasskeyLoginResult.Rejected
            }

            FirebaseUserStatus.DISABLED -> {
                // 無効化は再有効化できるため、パスキーは残して拒否のみ行う
                logger.warn("Rejected passkey login for disabled uid={}", firebaseUid)
                PasskeyLoginResult.Rejected
            }

            FirebaseUserStatus.ACTIVE -> {
                userDirectory
                    .createCustomToken(firebaseUid)
                    ?.let { PasskeyLoginResult.Success(it) }
                    ?: PasskeyLoginResult.Unavailable
            }
        }
    }
}
