package feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * ログインセッション単位の [ViewModelStoreOwner] を [content] に提供する。
 *
 * ルートの ViewModelStoreOwner はページ全体で 1 つしかないため、そのままでは
 * サインアウト後も各画面の ViewModel（エラー状態や前ユーザーのデータを含む）が残り、
 * 再ログイン時に使い回されてしまう。本 Composable がコンポジションから外れた時点
 * （= 認証済みツリーが破棄された時点）で [ViewModelStore.clear] し、
 * 次回ログイン時は ViewModel を新規生成させる。
 *
 * [remember] はキーなしのため、ユーザー単位で分離したい場合は呼び出し側で `key(uid)` と併用すること。
 *
 * lifecycle 2.11.0 以降では同等の公式 API `rememberViewModelStoreOwner` が提供されている。
 * 本プロジェクトは lifecycle 2.10.0 のため自前実装としている。
 */
@Composable
fun SessionViewModelStoreOwner(content: @Composable () -> Unit) {
    val owner =
        remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
