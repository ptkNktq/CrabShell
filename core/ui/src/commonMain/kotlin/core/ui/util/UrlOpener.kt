package core.ui.util

/**
 * 外部 URL を新しいタブ/ウィンドウで開く。
 *
 * 各 actual 実装は以下の挙動契約を満たすこと:
 * - 現在のページ/状態を破棄せず、別タブ・別ウィンドウで開くこと
 * - reverse tabnabbing を防ぐため、開く側のドキュメントから新タブを操作できないようにすること
 * - 同期的に呼ばれる前提だが、ユーザー操作起点でないとブラウザにブロックされる場合がある
 */
expect fun openExternalUrl(url: String)
