package core.ui.util

// JVM ターゲットはテスト用ビルドのみで実 UI を起動しない。Desktop ターゲットを足す場合は
// java.awt.Desktop.getDesktop().browse(java.net.URI(url)) 等の実装に差し替えること。
actual fun openExternalUrl(url: String) {
}
