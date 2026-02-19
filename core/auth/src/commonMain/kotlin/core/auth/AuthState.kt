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

object AuthStateHolder {
    var state by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    var idToken by mutableStateOf<String?>(null)
        internal set

    /** パスキーでログインした場合 true。パスキーセットアップ画面のスキップに使用。 */
    var signedInViaPasskey by mutableStateOf(false)

    fun setAuthenticated(
        user: User,
        token: String,
    ) {
        idToken = token
        state = AuthState.Authenticated(user)
    }

    fun setUnauthenticated() {
        idToken = null
        signedInViaPasskey = false
        state = AuthState.Unauthenticated
    }

    fun setLoading() {
        state = AuthState.Loading
    }
}
