package server.util

/** "YYYY-MM" を "YYYY年MM月" 表記（月は 0 埋め 2 桁）に整形。パース失敗時は入力をそのまま返す。 */
fun formatYearMonth(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val year = parts[0].toIntOrNull() ?: return yearMonth
    val month = parts[1].toIntOrNull() ?: return yearMonth
    return "%d年%02d月".format(year, month)
}

/** 金額を 3 桁区切り + "円" 付きで整形（例: 12345 → "12,345 円"）。 */
fun formatAmount(amount: Long): String = "%,d 円".format(amount)
