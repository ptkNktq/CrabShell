package core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 連続クリック抑止のデフォルト間隔（ミリ秒） */
const val DEFAULT_CLICK_DEBOUNCE_MILLIS = 300L

/**
 * onClick をデバウンス付きラムダに包んで返す。
 *
 * 判定はクリックイベントハンドラ内で完結するため、enabled ベースの制御と違い
 * 再コンポーズの反映を待たずに同一フレーム内の連続クリックも弾ける。
 * [debounceMillis] に 0 を渡すとデバウンスなし（連打が正当な操作であるナビゲーション等向け）。
 */
@Composable
fun rememberDebouncedOnClick(
    debounceMillis: Long = DEFAULT_CLICK_DEBOUNCE_MILLIS,
    onClick: () -> Unit,
): () -> Unit {
    val currentOnClick by rememberUpdatedState(onClick)
    val state = remember { ClickDebounceState() }
    return remember(debounceMillis) {
        {
            if (state.tryClick(debounceMillis)) {
                currentOnClick()
            }
        }
    }
}

/**
 * 前回受理したクリックからの経過時間で受理可否を判定する。
 * Snapshot 状態を使わないため、判定・記録によって再コンポーズは発生しない。
 */
class ClickDebounceState(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var lastAcceptedMark: TimeMark? = null

    /** クリックを受理できるなら記録して true、デバウンス間隔内なら false を返す */
    fun tryClick(debounceMillis: Long): Boolean {
        val mark = lastAcceptedMark
        if (mark != null && mark.elapsedNow().inWholeMilliseconds < debounceMillis) {
            return false
        }
        lastAcceptedMark = timeSource.markNow()
        return true
    }
}
