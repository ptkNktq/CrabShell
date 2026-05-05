package core.ui.util

/** 外部 URL を新しいタブ/ウィンドウで開く。reverse tabnabbing 対策として noopener/noreferrer を付与する。 */
expect fun openExternalUrl(url: String)
