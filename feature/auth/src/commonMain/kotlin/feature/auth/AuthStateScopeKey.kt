package feature.auth

import core.auth.AuthState

/**
 * 認証状態ごとに ViewModelStore を分けるためのスコープキー。
 * キーが変わると [ScopedViewModelStoreOwner] が作り直され、配下の ViewModel がすべて破棄される。
 *
 * Authenticated はユーザー単位でキーを分ける。トークンリフレッシュで Authenticated が
 * 再生成されても uid が同じならキーは変わらず、ViewModel は維持される。
 */
internal fun AuthState.viewModelScopeKey(): String =
    when (this) {
        is AuthState.Loading -> "loading"
        is AuthState.Unauthenticated -> "unauthenticated"
        is AuthState.Authenticated -> "authenticated:${user.uid}"
    }
