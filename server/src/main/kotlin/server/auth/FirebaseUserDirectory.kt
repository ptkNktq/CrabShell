package server.auth

/** Firebase Auth 上のユーザーアカウントの状態 */
enum class FirebaseUserStatus {
    /** 存在し、有効なユーザー */
    ACTIVE,

    /** 存在するが無効化されているユーザー */
    DISABLED,

    /** 存在しない（削除済みを含む）ユーザー */
    NOT_FOUND,
}

/**
 * Firebase Auth のユーザー照会とカスタムトークン発行。
 * Firebase Admin SDK への直接依存を切り離し、呼び出し側のロジックをモックでテストできるようにする。
 */
interface FirebaseUserDirectory {
    /**
     * uid のユーザーの状態を返す。
     * Firebase 未初期化や通信エラーなど、状態を判定できない場合は例外を投げる。
     */
    fun getUserStatus(uid: String): FirebaseUserStatus

    /** カスタムトークンを発行する。発行できない場合は null を返す。 */
    fun createCustomToken(uid: String): String?
}
