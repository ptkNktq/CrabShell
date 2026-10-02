package server.passkey

import org.slf4j.LoggerFactory
import server.auth.FirebaseAuthRepository
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
 * パスキー認証（署名検証）に成功したクレデンシャルの所有者について、ログインを許可するか判定する。
 *
 * 存在しない uid のカスタムトークンでサインインすると Firebase Auth にユーザーが新規作成されるため、
 * カスタムトークンは所有者が実在し有効であることを確認してから発行する。
 */
class PasskeyLoginService(
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val credentialRepository: PasskeyCredentialRepository,
) {
    private val logger = LoggerFactory.getLogger(PasskeyLoginService::class.java)

    /**
     * [firebaseUid] のユーザーの状態を Firebase Auth で確認し、ログインを許可するか判定する。
     *
     * - 有効なユーザー: カスタムトークンを発行し [PasskeyLoginResult.Success] を返す
     * - 存在しないユーザー: その uid のパスキーを passkey DB からすべて削除し、[PasskeyLoginResult.Rejected] を返す。
     *   削除に失敗しても Rejected を返す
     * - 無効化されたユーザー: パスキーは残したまま [PasskeyLoginResult.Rejected] を返す
     * - 状態の確認またはトークンの発行に失敗: パスキーは削除せず [PasskeyLoginResult.Unavailable] を返す
     */
    fun authorizeLogin(firebaseUid: String): PasskeyLoginResult {
        val status =
            try {
                firebaseAuthRepository.getUserStatus(firebaseUid)
            } catch (e: Exception) {
                logger.warn("Failed to get user status for uid={}", firebaseUid, e)
                return PasskeyLoginResult.Unavailable
            }

        return when (status) {
            FirebaseUserStatus.NOT_FOUND -> {
                // 削除済みユーザーのパスキーは二度と使えないため、ログイン時に検出して削除する。
                // 削除に失敗してもログインは拒否する（次回のログイン試行で再度削除を試みる）
                try {
                    val deleted = credentialRepository.deleteByUid(firebaseUid)
                    logger.warn("Rejected passkey login for non-existent uid={}; deleted {} credential(s)", firebaseUid, deleted)
                } catch (e: Exception) {
                    logger.warn("Rejected passkey login for non-existent uid={}; failed to delete credentials", firebaseUid, e)
                }
                PasskeyLoginResult.Rejected
            }

            FirebaseUserStatus.DISABLED -> {
                // 無効化は再有効化できるため、パスキーは残して拒否のみ行う
                logger.warn("Rejected passkey login for disabled uid={}", firebaseUid)
                PasskeyLoginResult.Rejected
            }

            FirebaseUserStatus.ACTIVE -> {
                firebaseAuthRepository
                    .createCustomToken(firebaseUid)
                    ?.let { PasskeyLoginResult.Success(it) }
                    ?: PasskeyLoginResult.Unavailable
            }
        }
    }
}
