package core.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import model.User

sealed class AuthState {
    data object Loading : AuthState()

    data object Unauthenticated : AuthState()

    data class Authenticated(
        val user: User,
    ) : AuthState()
}

class AuthStateHolder {
    var state by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    val currentUser: User?
        get() = (state as? AuthState.Authenticated)?.user

    val isAdmin: Boolean
        get() = currentUser?.isAdmin == true

    /** パスキーでログインした場合 true。パスキーセットアップ画面のスキップに使用。 */
    var signedInViaPasskey by mutableStateOf(false)

    // ID トークンはここに保持しない。Firebase が裏で行う自動更新を取りこぼして期限切れトークンを
    // 送り続けないよう、API リクエストごとに AuthRepository.getIdToken() で取得する。
    fun setAuthenticated(user: User) {
        state = AuthState.Authenticated(user)
    }

    fun setUnauthenticated() {
        signedInViaPasskey = false
        state = AuthState.Unauthenticated
    }

    fun setLoading() {
        state = AuthState.Loading
    }
}
