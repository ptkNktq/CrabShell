@file:OptIn(ExperimentalWasmJsInterop::class)

package core.common

@JsFun("(url) => { window.open(url, '_blank', 'noopener,noreferrer'); }")
actual external fun openExternalUrl(url: String)
