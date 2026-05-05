package core.common

// JVM ターゲットはテスト用ビルドのみで実 UI を起動しない。
// もし呼ばれた場合は事故防止のため fail-fast にする（無言の no-op だと、将来 Desktop ターゲットを足したときに
// リンククリックが無反応というバグに気付きにくい）。Desktop ターゲットを追加する場合は、
// java.awt.Desktop.getDesktop().browse(java.net.URI(url)) 等の実装に差し替えること。
actual fun openExternalUrl(url: String): Unit =
    error("openExternalUrl is not implemented for JVM target. Implement Desktop.browse if running on Desktop UI.")
