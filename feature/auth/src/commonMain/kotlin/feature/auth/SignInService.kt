package feature.auth

import core.auth.AuthRepository
import core.common.AppLogger
import core.network.LoginHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import model.LoginMethod

private const val TAG = "SignInService"

/**
 * サインインとログイン履歴の記録を、呼び出し元（[LoginViewModel]）のライフサイクルから切り離して実行する。
 *
 * サインインに成功すると認証状態が Authenticated に切り替わり、ログイン画面と [LoginViewModel] は
 * その場で破棄される（viewModelScope もキャンセルされる）。切り替わりは Firebase の
 * onAuthStateChanged から非同期に起きるため、サインイン API の応答より先に破棄されることもある。
 * viewModelScope 上でサインイン → 履歴記録を行うと記録が失われるため、アプリ全体で生存する
 * [externalScope] 上で実行し、呼び出し元はその結果を await するだけにする。
 *
 * @param externalScope 画面より長く生存するスコープ。子の失敗が兄弟に波及しないよう SupervisorJob を持たせること。
 */
class SignInService(
    private val authRepository: AuthRepository,
    private val loginHistoryRepository: LoginHistoryRepository,
    private val externalScope: CoroutineScope,
) {
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<Unit> = signInOutsideCaller(LoginMethod.EMAIL) { authRepository.signIn(email, password) }

    suspend fun signInWithCustomToken(token: String): Result<Unit> =
        signInOutsideCaller(LoginMethod.PASSKEY) { authRepository.signInWithCustomToken(token) }

    /**
     * [signIn] を [externalScope] 上で実行し、成功したら履歴記録を投げっぱなしで開始する。
     * 呼び出し元がキャンセルされても await が中断されるだけで、サインインと記録は完走する。
     * 記録の完了は待たない（記録の失敗・遅延でログインをブロックしない）。
     */
    private suspend fun signInOutsideCaller(
        method: LoginMethod,
        signIn: suspend () -> Result<Unit>,
    ): Result<Unit> =
        externalScope
            .async {
                val result = signIn()
                if (result.isSuccess) {
                    externalScope.launch { recordLogin(method) }
                }
                result
            }.await()

    private suspend fun recordLogin(method: LoginMethod) {
        try {
            loginHistoryRepository.recordLogin(method)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Kotlin/Wasm では JS 由来の例外が Exception ではない JsException（Throwable 直下）で届くため Throwable で捕捉する
            AppLogger.w(TAG, "Failed to record login history: ${e.message}")
        }
    }
}
