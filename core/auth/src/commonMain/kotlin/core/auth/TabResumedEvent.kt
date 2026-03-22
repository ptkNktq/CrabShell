package core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * バックグラウンド復帰時のイベントバス。
 * AuthenticatedApp がトークンリフレッシュ完了後に emit し、
 * 各 ViewModel が collect してデータを再取得する。
 */
class TabResumedEvent {
    private val _events = MutableSharedFlow<Unit>()
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    suspend fun emit() {
        _events.emit(Unit)
    }
}
