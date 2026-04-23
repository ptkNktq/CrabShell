package core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ごはん設定（mealOrder, mealTimes, リマインダー等）が保存されたときのイベントバス。
 * 設定画面が保存成功時に emit し、同一タブ内で開かれているダッシュボード・ごはん画面の
 * ViewModel が collect して即時に UiState を再取得する。
 *
 * subscriber が未生成の状態（Dashboard/Feeding 画面を未訪問）でも emit が hang しないよう、
 * `extraBufferCapacity=1` + `DROP_OLDEST` で最新 1 件のみ保持する。内容は「再取得のトリガ」
 * という単一責務なので、取りこぼしても次に subscribe した VM が init 時の getSettings() で
 * 最新値を取るためデータ整合性は壊れない。
 */
class FeedingSettingsChangedEvent {
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
