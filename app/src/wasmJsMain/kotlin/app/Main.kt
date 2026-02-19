package app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.di.appModules
import core.auth.AuthRepository
import feature.auth.AuthenticatedApp
import kotlinx.browser.document
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Koin DI の初期化
    val koinApp = startKoin { modules(appModules) }

    // Firebase 認証状態の監視を開始
    koinApp.koin.get<AuthRepository>().startListening()

    // ブラウザ履歴ナビゲーションの初期化（popstate リスナー登録）
    Navigator.init()

    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        KoinContext {
            AuthenticatedApp {
                App()
            }
        }
    }
}
