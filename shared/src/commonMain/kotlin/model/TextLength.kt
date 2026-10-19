package model

/**
 * Unicode のコードポイント数。
 *
 * [String.length] は UTF-16 のコード単位数のため、絵文字などサロゲートペアで表す文字を 2 と数える。
 * ここではサロゲートペアを 1 文字として数える（対になっていないサロゲートも 1 文字とする）。
 */
fun String.codePointLength(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}
