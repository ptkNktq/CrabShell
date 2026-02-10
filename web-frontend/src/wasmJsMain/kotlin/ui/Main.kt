package ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import ui.features.auth.AuthRepository
import ui.features.auth.AuthenticatedApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Firebase 認証状態の監視を開始
    AuthRepository.startListening()

    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        AuthenticatedApp()
    }
}
