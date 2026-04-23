package core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ごはん設定（mealOrder, mealTimes, リマインダー等）が保存されたときのイベントバス。
 * 設定画面が保存成功時に emit し、同一タブ内で開かれているダッシュボード・ごはん画面の
 * ViewModel が collect して即時に UiState を再取得する。タブ復帰を待たせないための補助。
 */
class FeedingSettingsChangedEvent {
    private val _events = MutableSharedFlow<Unit>()
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    suspend fun emit() {
        _events.emit(Unit)
    }
}
