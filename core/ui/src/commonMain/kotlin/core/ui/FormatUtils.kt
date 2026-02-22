package core.ui

/** 金額をカンマ区切りの円表示にフォーマットする。負数は "-¥1,000" のように¥の前にマイナスを付ける。 */
fun formatYen(amount: Long): String {
    val abs = if (amount < 0) -amount else amount
    val str = abs.toString()
    val result = StringBuilder()
    for ((i, c) in str.reversed().withIndex()) {
        if (i > 0 && i % 3 == 0) result.append(',')
        result.append(c)
    }
    result.reverse()
    return if (amount < 0) "-¥$result" else "¥$result"
}
