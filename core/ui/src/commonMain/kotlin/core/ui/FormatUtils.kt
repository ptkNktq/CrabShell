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

/** 金額入力フィールドの表示用に生桁文字列をカンマ区切りにフォーマットする。¥記号は付けない。 */
fun formatAmountInput(raw: String): String {
    if (raw.isEmpty() || raw == "-") return raw
    val isNegative = raw.startsWith("-")
    val digits = if (isNegative) raw.drop(1) else raw
    if (digits.isEmpty()) return raw
    val result = StringBuilder()
    for ((i, c) in digits.reversed().withIndex()) {
        if (i > 0 && i % 3 == 0) result.append(',')
        result.append(c)
    }
    result.reverse()
    return if (isNegative) "-$result" else result.toString()
}
