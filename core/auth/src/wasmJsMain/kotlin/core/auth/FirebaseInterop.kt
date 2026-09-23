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

// 現在のユーザーの IDトークンを取得 → Promise<{ token, error }> を返す（未サインイン時は null）
// 失敗時も reject せず、Firebase のエラーコード（例: auth/network-request-failed）を error に入れて resolve する。
// Kotlin/Wasm 側で JS 例外からエラーコードを取り出せないため、JS 側で変換する。
@JsFun(
    """(auth, forceRefresh) => {
    if (!auth.currentUser) return Promise.resolve(null);
    return auth.currentUser.getIdToken(forceRefresh).then(
        (token) => ({ token: token, error: null }),
        (e) => ({ token: null, error: (e && e.code) || 'unknown' })
    );
}""",
)
external fun getIdTokenOrError(
    auth: JsAny,
    forceRefresh: Boolean,
): Promise<JsAny?>

// getIdTokenOrError の結果からエラーコードを取得（成功時は null）
@JsFun("(obj) => obj.error")
external fun getErrorCodeFromResult(obj: JsAny): JsString?

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

// IDトークンを強制リフレッシュ → Promise<{ token, isAdmin }> を返す
@JsFun(
    """(auth) => {
    if (!auth.currentUser) return Promise.resolve(null);
    return auth.currentUser.getIdTokenResult(true).then((result) => ({
        token: result.token,
        isAdmin: result.claims.admin === true
    }));
}""",
)
external fun forceRefreshIdToken(auth: JsAny): Promise<JsAny?>

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
