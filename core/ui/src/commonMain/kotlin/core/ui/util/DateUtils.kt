package core.ui.util

import kotlin.time.Clock
import kotlin.time.Instant

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
private const val JST_OFFSET_MILLIS = 9 * MILLIS_PER_HOUR
private const val FEEDING_DAY_BOUNDARY_HOUR = 5

private val DAY_OF_WEEK_LABELS = arrayOf("日", "月", "火", "水", "木", "金", "土")
private val MONTH_DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private data class CivilDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun daysInMonthOf(
    year: Int,
    month: Int,
): Int = if (month == 2 && isLeapYear(year)) 29 else MONTH_DAYS[month - 1]

// Howard Hinnant's days_from_civil / civil_from_days algorithm (public domain).
// http://howardhinnant.github.io/date_algorithms.html
private fun daysFromCivil(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

private fun civilFromDays(epochDay: Long): CivilDate {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = if (month <= 2) y + 1 else y
    return CivilDate(year.toInt(), month.toInt(), day)
}

// epoch day 0 (1970-01-01) was a Thursday (index 4). Returns 0=Sun..6=Sat.
private fun dayOfWeekFromEpochDay(epochDay: Long): Int = (epochDay + 4).mod(7L).toInt()

private fun formatDate(
    year: Int,
    month: Int,
    day: Int,
): String = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

private fun parseDate(dateStr: String): CivilDate =
    CivilDate(
        year = dateStr.substring(0, 4).toInt(),
        month = dateStr.substring(5, 7).toInt(),
        day = dateStr.substring(8, 10).toInt(),
    )

private fun formatHHMM(msOfDay: Long): String {
    val hour = msOfDay / MILLIS_PER_HOUR
    val minute = msOfDay.mod(MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun jstNowMillis(now: Instant): Long = now.toEpochMilliseconds() + JST_OFFSET_MILLIS

/** 今日の日付を YYYY-MM-DD 形式で返す（JST） */
fun todayDate(now: Instant = Clock.System.now()): String {
    val (y, m, d) = civilFromDays(jstNowMillis(now).floorDiv(MILLIS_PER_DAY))
    return formatDate(y, m, d)
}

/** 餌やり日付を YYYY-MM-DD 形式で返す（JST 5時を日付境界とする） */
fun feedingDate(now: Instant = Clock.System.now()): String {
    val millis = jstNowMillis(now)
    var epochDay = millis.floorDiv(MILLIS_PER_DAY)
    val hourOfDay = millis.mod(MILLIS_PER_DAY) / MILLIS_PER_HOUR
    if (hourOfDay < FEEDING_DAY_BOUNDARY_HOUR) epochDay -= 1
    val (y, m, d) = civilFromDays(epochDay)
    return formatDate(y, m, d)
}

/** 日付文字列を days 日ずらす */
fun shiftDate(
    dateStr: String,
    days: Int,
): String {
    val parsed = parseDate(dateStr)
    val (y, m, d) = civilFromDays(daysFromCivil(parsed.year, parsed.month, parsed.day) + days)
    return formatDate(y, m, d)
}

/** 指定月の1日の曜日を返す (0=Sun, 1=Mon, ..., 6=Sat) */
fun firstDayOfWeek(
    year: Int,
    month: Int,
): Int = dayOfWeekFromEpochDay(daysFromCivil(year, month, 1))

/** 指定月の日数を返す */
fun daysInMonth(
    year: Int,
    month: Int,
): Int = daysInMonthOf(year, month)

/** 日付文字列から短縮曜日名を返す (e.g. "月", "火") */
fun dayOfWeekShort(dateStr: String): String {
    val parsed = parseDate(dateStr)
    val dow = dayOfWeekFromEpochDay(daysFromCivil(parsed.year, parsed.month, parsed.day))
    return DAY_OF_WEEK_LABELS[dow]
}

/** 現在時刻を HH:MM 形式で返す (JST) */
fun currentTime(now: Instant = Clock.System.now()): String = formatHHMM(jstNowMillis(now).mod(MILLIS_PER_DAY))

/** 今日の日付を "M月D日（曜）" 形式で返す (JST) */
fun formattedToday(now: Instant = Clock.System.now()): String {
    val epochDay = jstNowMillis(now).floorDiv(MILLIS_PER_DAY)
    val (_, m, d) = civilFromDays(epochDay)
    val dow = dayOfWeekFromEpochDay(epochDay)
    return "${m}月${d}日（${DAY_OF_WEEK_LABELS[dow]}）"
}

/** 今日の年を返す (JST) */
fun currentYear(now: Instant = Clock.System.now()): String = civilFromDays(jstNowMillis(now).floorDiv(MILLIS_PER_DAY)).year.toString()

/** 今日が月内の第何週か返す (1-5)。日曜始まりで計算。 */
fun weekOfMonth(now: Instant = Clock.System.now()): Int {
    val day = civilFromDays(jstNowMillis(now).floorDiv(MILLIS_PER_DAY)).day
    return (day + 6) / 7
}

/** 今日の曜日を 0(日)〜6(土) で返す */
fun dayOfWeekIndex(now: Instant = Clock.System.now()): Int = dayOfWeekFromEpochDay(jstNowMillis(now).floorDiv(MILLIS_PER_DAY))

/** 明日の曜日を 0(日)〜6(土) で返す */
fun tomorrowDayOfWeekIndex(now: Instant = Clock.System.now()): Int = dayOfWeekFromEpochDay(jstNowMillis(now).floorDiv(MILLIS_PER_DAY) + 1)

/** 明日が月内の第何週か返す (1-5)。日曜始まりで計算。 */
fun tomorrowWeekOfMonth(now: Instant = Clock.System.now()): Int {
    val day = civilFromDays(jstNowMillis(now).floorDiv(MILLIS_PER_DAY) + 1).day
    return (day + 6) / 7
}

/** ISO タイムスタンプを JST の HH:MM 形式に変換 */
fun toJstHHMM(iso: String): String = formatHHMM((Instant.parse(iso).toEpochMilliseconds() + JST_OFFSET_MILLIS).mod(MILLIS_PER_DAY))

/** ISO タイムスタンプから JST の時を取得 */
fun toJstHour(iso: String): String {
    val msOfDay = (Instant.parse(iso).toEpochMilliseconds() + JST_OFFSET_MILLIS).mod(MILLIS_PER_DAY)
    return (msOfDay / MILLIS_PER_HOUR).toString().padStart(2, '0')
}

/** ISO タイムスタンプから JST の分を取得 */
fun toJstMinute(iso: String): String {
    val msOfDay = (Instant.parse(iso).toEpochMilliseconds() + JST_OFFSET_MILLIS).mod(MILLIS_PER_DAY)
    return (msOfDay.mod(MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toString().padStart(2, '0')
}

// deadline を JST wall-clock 前提の仮想エポックミリ秒（jstNowMillis と同じ基準系）に変換する
private fun deadlineToJstMillis(deadline: String): Long {
    val parsed = parseDate(deadline)
    val epochDay = daysFromCivil(parsed.year, parsed.month, parsed.day)
    return if (deadline.length > 10) {
        val timePart = deadline.substring(11)
        val hour = timePart.substring(0, 2).toInt()
        val minute = timePart.substring(3, 5).toInt()
        epochDay * MILLIS_PER_DAY + hour * MILLIS_PER_HOUR + minute * MILLIS_PER_MINUTE
    } else {
        epochDay * MILLIS_PER_DAY + 23 * MILLIS_PER_HOUR + 59 * MILLIS_PER_MINUTE + 59_000L
    }
}

/**
 * 期限文字列("YYYY-MM-DD" or "YYYY-MM-DD HH:MM")から残り時間テキストを返す。
 * 1日以上: "あとX日"、1日以内: "あとX時間"、期限切れ: "期限切れ"
 */
fun remainingTime(
    deadline: String,
    now: Instant = Clock.System.now(),
): String {
    val diff = deadlineToJstMillis(deadline) - jstNowMillis(now)
    if (diff <= 0) return "期限切れ"
    val hours = diff / MILLIS_PER_HOUR
    if (hours < 24) return "あと${hours}時間"
    return "あと${hours / 24}日"
}
