@file:OptIn(ExperimentalWasmJsInterop::class)

package core.ui.util

@JsFun("(url) => { window.open(url, '_blank', 'noopener,noreferrer'); }")
actual external fun openExternalUrl(url: String)
