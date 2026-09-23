package core.common

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

private const val TAG = "ApplicationScope"

/**
 * 画面（ViewModel）より長く生存させる処理を実行するための、アプリ全体で 1 つの [CoroutineScope]。
 * DI でシングルトンとして提供し、必要なサービスに注入して使う（例: サインイン直後のログイン履歴記録）。
 *
 * - 子の失敗が兄弟に波及しないよう [SupervisorJob] を持つ
 * - UI 状態（Compose の snapshot state）に触れる処理もあるため Main で実行する。
 *   viewModelScope は Main.immediate だが、ここでは呼び出し元と同じフレームで即時実行する必要がないため Main とする
 * - launch した子で取りこぼした例外がグローバルハンドラへ抜けないよう、最終防御としてログに残す。
 *   async の例外は Deferred に保持され await 側へ再スローされるため、このハンドラには届かない
 */
class ApplicationScope : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() +
            Dispatchers.Main +
            CoroutineExceptionHandler { _, e ->
                AppLogger.e(TAG, "Uncaught exception: ${e.stackTraceToString()}")
            }
}
