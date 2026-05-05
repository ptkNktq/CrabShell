@file:OptIn(ExperimentalWasmJsInterop::class)

package core.ui.util

@JsFun(
    """(url) => {
    window.open(url, '_blank', 'noopener,noreferrer');
}""",
)
private external fun openExternalUrlJs(url: JsString)

actual fun openExternalUrl(url: String) {
    openExternalUrlJs(url.toJsString())
}
