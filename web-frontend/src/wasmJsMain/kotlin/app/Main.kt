package app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import core.auth.AuthRepository
import feature.auth.AuthenticatedApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Firebase 認証状態の監視を開始
    AuthRepository.startListening()

    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        AuthenticatedApp {
            App()
        }
    }
}
