package server.passkey

/**
 * 保存済みのパスキー（WebAuthn クレデンシャル）。
 * ByteArray を持つため data class にしない（自動生成の equals が配列を参照で比較してしまうため）。
 */
class PasskeyCredentialRecord(
    val id: Long,
    val firebaseUid: String,
    val credentialId: ByteArray,
    val credentialIdBase64: String,
    val publicKey: ByteArray,
    val counter: Long,
    val transports: String?,
    val createdAt: Long,
)

/**
 * 登録検証を通ったパスキーの情報（保存前）。
 * [WebAuthnVerifier] が生成し [PasskeyCredentialRepository.save] に渡す。
 */
class RegisteredCredential(
    val credentialId: ByteArray,
    val credentialIdBase64: String,
    val publicKey: ByteArray,
    val counter: Long,
)

/** パスキーのクレデンシャル保存先 */
interface PasskeyCredentialRepository {
    /** 指定したユーザーのパスキーの件数を返す */
    fun countByUid(firebaseUid: String): Int

    /** 指定したユーザーのパスキーを登録日時の昇順で返す */
    fun findByUid(firebaseUid: String): List<PasskeyCredentialRecord>

    /** credential ID（Base64URL）からパスキーを探す。見つからなければ null を返す */
    fun findByCredentialId(credentialIdBase64: String): PasskeyCredentialRecord?

    /**
     * 登録検証を通ったパスキーを保存する。
     * [transports] は検証結果ではなくクライアントが申告した値（認証時のヒントにのみ使う）のため、
     * [RegisteredCredential] には含めず別に受け取る。
     */
    fun save(
        firebaseUid: String,
        credential: RegisteredCredential,
        transports: String?,
    )

    /** 認証に成功したパスキーの署名カウンタを更新する */
    fun updateCounter(
        id: Long,
        counter: Long,
    )

    /**
     * 指定したユーザーのパスキーをすべて削除する。
     * @return 削除した件数
     */
    fun deleteByUid(firebaseUid: String): Int

    /**
     * 指定したユーザーが所有する 1 件のパスキーを削除する。
     * firebaseUid の一致も条件に含めることで、他ユーザーの credential ID を誤って削除できないようにする。
     * @return 削除できた場合 true、対象が存在しない（他ユーザーの ID を含む）場合 false
     */
    fun deleteByIdAndUid(
        id: Long,
        firebaseUid: String,
    ): Boolean
}
