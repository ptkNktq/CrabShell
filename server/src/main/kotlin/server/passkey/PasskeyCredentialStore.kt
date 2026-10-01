package server.passkey

/** パスキーのクレデンシャル保存先に対する操作のうち、ログイン判定で必要なもの */
interface PasskeyCredentialStore {
    /**
     * 指定したユーザーのパスキーをすべて削除する。
     * @return 削除した件数
     */
    fun deleteCredentials(firebaseUid: String): Int
}
