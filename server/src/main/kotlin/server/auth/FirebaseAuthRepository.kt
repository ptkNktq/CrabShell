package server.auth

import com.google.firebase.auth.FirebaseToken
import model.User

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
 * Firebase Auth への窓口（ID トークンの検証・ユーザーの照会と更新・カスタムトークンの発行）。
 * Firebase Admin SDK への直接依存を切り離し、呼び出し側のロジックをモックでテストできるようにする。
 */
interface FirebaseAuthRepository {
    /** ID トークンを検証する。無効なトークンや検証に失敗した場合は null を返す。 */
    fun verifyIdToken(idToken: String): FirebaseToken?

    /**
     * uid のユーザーの状態を返す。
     * 通信エラーなど、状態を判定できない場合は例外を投げる（null や NOT_FOUND に丸めると、
     * 呼び出し側が正規ユーザーのデータを削除しうるため）。
     */
    fun getUserStatus(uid: String): FirebaseUserStatus

    /** uid から displayName を取得する。未設定（null / 空文字）や取得失敗時は null を返す。 */
    fun getDisplayName(uid: String): String?

    /** 全ユーザーを取得する。取得に失敗した場合は例外を投げる。 */
    fun listUsers(): List<User>

    /** ユーザーの表示名を更新し、更新後のユーザーを返す。更新に失敗した場合は例外を投げる。 */
    fun updateDisplayName(
        uid: String,
        displayName: String,
    ): User

    /** カスタムトークンを発行する。発行できない場合は null を返す。 */
    fun createCustomToken(uid: String): String?
}
