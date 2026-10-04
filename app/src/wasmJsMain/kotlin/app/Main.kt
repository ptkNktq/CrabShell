package app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.di.appModules
import core.auth.AuthRepository
import feature.auth.AuthenticatedApp
import feature.auth.MCP_CONNECT_PATH
import feature.auth.McpConnectScreen
import kotlinx.browser.document
import kotlinx.browser.window
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
                // MCP 連携（WorkOS AuthKit の Login URI）はナビゲーションの外の単独画面として表示する
                if (window.location.pathname.trimEnd('/') == MCP_CONNECT_PATH) {
                    McpConnectScreen()
                } else {
                    App()
                }
            }
        }
    }
}
