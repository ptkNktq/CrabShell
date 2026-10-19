package server.util

private const val MAX_LOGGED_VALUE_LENGTH = 128

// C0/C1 制御文字に加え、行区切り（U+2028）・段落区切り（U+2029）も改行として扱われうるため対象にする
private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]")

/**
 * 外部から渡された値（未検証のトークンのクレーム、AI が渡したツール引数など）をログに出せる形にする。
 * 改行で偽のログ行を作られたり、長い値でログを膨らまされたりしないよう、
 * 制御文字を `?` に置き換え [MAX_LOGGED_VALUE_LENGTH] 文字に切り詰める。
 */
fun sanitizeForLog(value: String?): String? = value?.replace(CONTROL_CHARACTERS, "?")?.take(MAX_LOGGED_VALUE_LENGTH)
