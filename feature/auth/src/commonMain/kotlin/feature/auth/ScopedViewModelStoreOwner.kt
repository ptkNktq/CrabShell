package feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * [scopeKey] ごとに独立した [ViewModelStoreOwner] を [content] に提供する。
 * [scopeKey] が変わったとき、または本 Composable がコンポジションから外れたときに
 * [ViewModelStore.clear] し、配下の ViewModel をすべて破棄する。
 *
 * ルートの ViewModelStoreOwner はページ全体で 1 つしかないため、そのままでは
 * サインアウト後も各画面の ViewModel（エラー状態や前ユーザーのデータを含む）が残り、
 * 再ログイン時に使い回されてしまう。認証状態をキーに本 Composable で包むことで、
 * 認証状態が切り替わるたびに ViewModel を新規生成させる。
 *
 * lifecycle 2.11.0 以降では同等の公式 API `rememberViewModelStoreOwner` が提供されている。
 * 本プロジェクトは lifecycle 2.10.0 のため自前実装としている。
 */
@Composable
internal fun ScopedViewModelStoreOwner(
    scopeKey: Any,
    content: @Composable () -> Unit,
) {
    key(scopeKey) {
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
}
