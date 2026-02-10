package frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import frontend.auth.AuthRepository
import frontend.auth.AuthenticatedApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Firebase 認証状態の監視を開始
    AuthRepository.startListening()

    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        AuthenticatedApp()
    }
}
