package app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * AuthStateHolder と同パターンのグローバルナビゲーション状態。
 * popstate リスナーを Compose ライフサイクルの外で永続的に保持し、
 * Kotlin/WASM の addEventListener/removeEventListener ラッパー不一致問題を回避する。
 */
object Navigator {
    var currentScreen by mutableStateOf(Screen.Dashboard)
        private set

    /**
     * 現在画面内のサブルート（URL フラグメント、例: `/settings#Account` の `Account`）。
     * 該当なしは null。各画面が自前の識別子を解釈する。
     */
    var fragment by mutableStateOf<String?>(null)
        private set

    /** Main.kt から一度だけ呼ぶ。URL から初期状態を決定し、popstate/hashchange リスナーを登録する。 */
    fun init() {
        syncFromLocation()
        window.addEventListener("popstate", { _: Event -> syncFromLocation() })
        window.addEventListener("hashchange", { _: Event -> syncFromLocation() })
    }

    private fun syncFromLocation() {
        currentScreen = Screen.fromPath(window.location.pathname)
        fragment =
            window.location.hash
                .removePrefix("#")
                .ifEmpty { null }
    }

    fun navigateTo(screen: Screen) {
        if (screen != currentScreen) {
            window.history.pushState(null, "", screen.path)
            currentScreen = screen
            fragment = null
        }
    }

    /** 画面遷移を伴わず、現在画面内のサブルート（URL フラグメント）のみ更新する。 */
    fun setFragment(value: String?) {
        if (value == fragment) return
        val url = if (value.isNullOrEmpty()) currentScreen.path else "${currentScreen.path}#$value"
        window.history.pushState(null, "", url)
        fragment = value
    }
}
