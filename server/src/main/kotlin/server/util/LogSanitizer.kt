package server.util

private const val MAX_LOGGED_VALUE_LENGTH = 128

// C0/C1 制御文字に加え、行区切り（U+2028）・段落区切り（U+2029）も改行として扱われうるため対象にする
private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]")

/**
 * 外部から渡された値（未検証のトークンのクレーム、AI が渡したツール引数など）をログに出せる形にする。
 * 改行で偽のログ行を作られたり、長い値でログを膨らまされたりしないよう、
 * [MAX_LOGGED_VALUE_LENGTH] 文字（コードポイント数）に切り詰めてから制御文字を `?` に置き換える。
 *
 * 先に切り詰めて、長い値でも置き換えの走査を上限までにする。
 * 切り詰めはコードポイント単位で行い、絵文字などのサロゲートペアを途中で切らない。
 */
fun sanitizeForLog(value: String?): String? = value?.takeCodePoints(MAX_LOGGED_VALUE_LENGTH)?.replace(CONTROL_CHARACTERS, "?")

/** 先頭から [n] コードポイントまで。サロゲートペアは 1 つとして数える */
private fun String.takeCodePoints(n: Int): String {
    var end = 0
    var count = 0
    while (end < length && count < n) {
        end += Character.charCount(codePointAt(end))
        count++
    }
    return substring(0, end)
}
