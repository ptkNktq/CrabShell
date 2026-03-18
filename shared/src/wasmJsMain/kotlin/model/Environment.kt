package model

import kotlinx.browser.window

actual val isDevEnvironment: Boolean =
    window.location.port == "3000"
