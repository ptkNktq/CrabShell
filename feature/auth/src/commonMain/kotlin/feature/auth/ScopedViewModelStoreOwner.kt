package feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

/**
 * [scopeKey] ごとに独立した ViewModelStoreOwner を [content] に提供する。
 * [scopeKey] が変わったとき、または本 Composable がコンポジションから外れたときに、
 * 配下の ViewModel をすべて破棄する。
 *
 * ルートの ViewModelStoreOwner はページ全体で 1 つしかないため、そのままでは
 * サインアウト後も各画面の ViewModel（エラー状態や前ユーザーのデータを含む）が残り、
 * 再ログイン時に使い回されてしまう。認証状態をキーに本 Composable で包むことで、
 * 認証状態が切り替わるたびに ViewModel を新規生成させる。
 *
 * Owner の生成・破棄は lifecycle 公式の [rememberViewModelStoreOwner] に任せる。
 * 同 API は呼び出し位置に紐づく Owner を作り、コンポジションから外れたときに ViewModel を clear する。
 * [key] で包むことで、[scopeKey] の変化も「別の呼び出し位置」として扱わせている。
 *
 * @see <a href="https://developer.android.com/reference/kotlin/androidx/lifecycle/viewmodel/compose/rememberViewModelStoreOwner.composable">rememberViewModelStoreOwner</a>
 */
@Composable
internal fun ScopedViewModelStoreOwner(
    scopeKey: Any,
    content: @Composable () -> Unit,
) {
    key(scopeKey) {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides rememberViewModelStoreOwner(),
            content = content,
        )
    }
}
