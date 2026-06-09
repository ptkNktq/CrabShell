package core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * バックグラウンド復帰時のイベントバス。
 * AuthenticatedApp がトークンリフレッシュ完了後に emit し、
 * 各 ViewModel が collect してデータを再取得する。
 *
 * subscriber が未生成の状態（Dashboard/Feeding 画面を未訪問）でも emit が hang しないよう、
 * `extraBufferCapacity=1` + `DROP_OLDEST` で最新 1 件のみ保持する。
 */
class TabResumedEvent {
    private val _events =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun emit() {
        _events.tryEmit(Unit)
    }
}
