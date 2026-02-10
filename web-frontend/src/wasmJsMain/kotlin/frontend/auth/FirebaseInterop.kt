@file:OptIn(ExperimentalWasmJsInterop::class)

package frontend.auth

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

// Firebase compat SDK のグローバルオブジェクト
@JsFun("() => globalThis.firebase")
external fun getFirebase(): JsAny

// Firebase Auth インスタンスを取得
@JsFun("(fb) => fb.auth()")
external fun firebaseAuth(firebase: JsAny): JsAny

// メール/パスワードでサインイン → Promise を返す
@JsFun("(auth, email, password) => auth.signInWithEmailAndPassword(email, password)")
external fun signInWithEmailAndPassword(auth: JsAny, email: JsString, password: JsString): Promise<JsAny?>

// サインアウト → Promise を返す
@JsFun("(auth) => auth.signOut()")
external fun firebaseSignOut(auth: JsAny): Promise<JsAny?>

// 現在のユーザーの IDトークンを取得 → Promise<string> を返す
@JsFun("(auth) => auth.currentUser ? auth.currentUser.getIdToken() : Promise.resolve(null)")
external fun getIdToken(auth: JsAny): Promise<JsAny?>

// 認証状態の変更を監視するコールバック登録
@JsFun("""(auth, onUser, onNull) => {
    auth.onAuthStateChanged((user) => {
        if (user) {
            onUser(user.uid, user.email || '', user.displayName || '');
        } else {
            onNull();
        }
    });
}""")
external fun onAuthStateChanged(
    auth: JsAny,
    onUser: (JsString, JsString, JsString) -> Unit,
    onNull: () -> Unit,
)

// String → JsString 変換ヘルパー
@JsFun("(str) => str")
external fun stringToJs(str: String): JsString

fun String.toJsString(): JsString = stringToJs(this)
