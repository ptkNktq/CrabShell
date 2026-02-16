@file:OptIn(ExperimentalWasmJsInterop::class)

package core.auth

import kotlin.js.Promise

// Firebase compat SDK のグローバルオブジェクト
@JsFun("() => globalThis.firebase")
external fun getFirebase(): JsAny

// Firebase Auth インスタンスを取得
@JsFun("(fb) => fb.auth()")
external fun firebaseAuth(firebase: JsAny): JsAny

// メール/パスワードでサインイン → Promise を返す
@JsFun("(auth, email, password) => auth.signInWithEmailAndPassword(email, password)")
external fun signInWithEmailAndPassword(
    auth: JsAny,
    email: JsString,
    password: JsString,
): Promise<JsAny?>

// サインアウト → Promise を返す
@JsFun("(auth) => auth.signOut()")
external fun firebaseSignOut(auth: JsAny): Promise<JsAny?>

// 現在のユーザーの IDトークンを取得 → Promise<string> を返す
@JsFun("(auth) => auth.currentUser ? auth.currentUser.getIdToken() : Promise.resolve(null)")
external fun getIdToken(auth: JsAny): Promise<JsAny?>

// IDトークン結果（token + custom claims）を取得 → Promise<{ token, isAdmin }> を返す
@JsFun(
    """(auth) => {
    if (!auth.currentUser) return Promise.resolve(null);
    return auth.currentUser.getIdTokenResult().then((result) => ({
        token: result.token,
        isAdmin: result.claims.admin === true
    }));
}""",
)
external fun getIdTokenResult(auth: JsAny): Promise<JsAny?>

// IDトークン結果からトークン文字列を取得
@JsFun("(obj) => obj.token")
external fun getTokenFromResult(obj: JsAny): JsString

// IDトークン結果から isAdmin フラグを取得
@JsFun("(obj) => obj.isAdmin")
external fun getIsAdminFromResult(obj: JsAny): JsBoolean

// 認証状態の変更を監視するコールバック登録
@JsFun(
    """(auth, onUser, onNull) => {
    auth.onAuthStateChanged((user) => {
        if (user) {
            onUser(user.uid, user.email || '', user.displayName || '');
        } else {
            onNull();
        }
    });
}""",
)
external fun onAuthStateChanged(
    auth: JsAny,
    onUser: (JsString, JsString, JsString) -> Unit,
    onNull: () -> Unit,
)

// Custom Token でサインイン → Promise を返す
@JsFun("(auth, token) => auth.signInWithCustomToken(token)")
external fun signInWithCustomToken(
    auth: JsAny,
    token: JsString,
): Promise<JsAny?>

// 再認証してからパスワードを変更 → Promise を返す
// EmailAuthProvider.credential が invalid-credential になるため、
// signInWithEmailAndPassword で再認証してから updatePassword を呼ぶ
@JsFun(
    """(auth, currentPassword, newPassword) => {
    const user = auth.currentUser;
    if (!user || !user.email) return Promise.reject(new Error('ログイン中のユーザーが見つかりません'));
    return auth.signInWithEmailAndPassword(user.email, currentPassword)
        .then(() => auth.currentUser.updatePassword(newPassword));
}""",
)
external fun reauthenticateAndChangePassword(
    auth: JsAny,
    currentPassword: JsString,
    newPassword: JsString,
): Promise<JsAny?>

// String → JsString 変換ヘルパー
@JsFun("(str) => str")
external fun stringToJs(str: String): JsString

fun String.toJsString(): JsString = stringToJs(this)
