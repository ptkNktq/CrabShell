package feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * 本 Composable がコンポジションに存在する間だけ有効な [ViewModelStoreOwner] を [content] に提供する。
 * コンポジションから外れた時点で [ViewModelStore.clear] し、配下の ViewModel をすべて破棄する。
 *
 * ルートの ViewModelStoreOwner はページ全体で 1 つしかないため、そのままでは
 * サインアウト後も各画面の ViewModel（エラー状態や前ユーザーのデータを含む）が残り、
 * 再ログイン時に使い回されてしまう。認証状態ごとに本 Composable で包むことで、
 * 認証状態が切り替わるたびに ViewModel を新規生成させる。
 *
 * [remember] はキーなしのため、同じ位置のまま別スコープに切り替えたい場合（ユーザー切り替え等）は
 * 呼び出し側で `key()` と併用すること。
 *
 * lifecycle 2.11.0 以降では同等の公式 API `rememberViewModelStoreOwner` が提供されている。
 * 本プロジェクトは lifecycle 2.10.0 のため自前実装としている。
 */
@Composable
fun ScopedViewModelStoreOwner(content: @Composable () -> Unit) {
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
