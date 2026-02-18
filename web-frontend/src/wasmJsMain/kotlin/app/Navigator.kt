package app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

enum class Screen(val title: String, val path: String) {
    Dashboard("ダッシュボード", "/dashboard"),
    Feeding("ごはん", "/feeding"),
    Payment("お支払い", "/payment"),
    Report("家計レポート", "/report"),
    Money("お金の管理", "/money"),
    Settings("設定", "/settings"),
    ;

    companion object {
        fun fromPath(path: String): Screen = entries.find { it.path == path } ?: Dashboard
    }
}

/**
 * AuthStateHolder と同パターンのグローバルナビゲーション状態。
 * popstate リスナーを Compose ライフサイクルの外で永続的に保持し、
 * Kotlin/WASM の addEventListener/removeEventListener ラッパー不一致問題を回避する。
 */
object Navigator {
    var currentScreen by mutableStateOf(Screen.Dashboard)
        private set

    /** Main.kt から一度だけ呼ぶ。URL から初期画面を決定し、popstate リスナーを登録する。 */
    fun init() {
        currentScreen = Screen.fromPath(window.location.pathname)
        window.addEventListener("popstate", { _: Event ->
            currentScreen = Screen.fromPath(window.location.pathname)
        })
    }

    fun navigateTo(screen: Screen) {
        if (screen != currentScreen) {
            window.history.pushState(null, "", screen.path)
            currentScreen = screen
        }
    }
}
