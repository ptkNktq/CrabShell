package core.ui.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.YearMonth
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// 日本は DST が無いため JST は常に固定 UTC+9。タイムゾーンDB不要の FixedOffsetTimeZone。
private val JST: TimeZone = UtcOffset(hours = 9).asTimeZone()

internal val DAY_OF_WEEK_LABELS = arrayOf("日", "月", "火", "水", "木", "金", "土")

// kotlinx.datetime.DayOfWeek は MONDAY=0..SUNDAY=6 の並び。0=日,1=月,...,6=土 に変換する。
private fun DayOfWeek.toSundayIndex(): Int = (ordinal + 1) % 7

private fun jstNow(now: Instant): LocalDateTime = now.toLocalDateTime(JST)

private fun jstFromIso(iso: String): LocalDateTime = Instant.parse(iso).toLocalDateTime(JST)

private fun formatDate(date: LocalDate): String =
    "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

private fun parseDate(dateStr: String): LocalDate =
    LocalDate(dateStr.substring(0, 4).toInt(), dateStr.substring(5, 7).toInt(), dateStr.substring(8, 10).toInt())

private fun formatHHMM(
    hour: Int,
    minute: Int,
): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 今日の日付を YYYY-MM-DD 形式で返す（JST） */
fun todayDate(now: Instant = Clock.System.now()): String = formatDate(jstNow(now).date)

/** boundaryHour 時を日付の境界として、現在の「営業日」を YYYY-MM-DD 形式で返す（JST） */
fun dateWithHourBoundary(
    now: Instant = Clock.System.now(),
    boundaryHour: Int = 5,
): String {
    val jst = jstNow(now)
    val date = if (jst.hour < boundaryHour) jst.date.minus(1, DateTimeUnit.DAY) else jst.date
    return formatDate(date)
}

/** 日付文字列を days 日ずらす */
fun shiftDate(
    dateStr: String,
    days: Int,
): String = formatDate(parseDate(dateStr).plus(days, DateTimeUnit.DAY))

/** 指定月の1日の曜日を返す (0=Sun, 1=Mon, ..., 6=Sat) */
fun firstDayOfWeek(
    year: Int,
    month: Int,
): Int = LocalDate(year, month, 1).dayOfWeek.toSundayIndex()

/** 指定月の日数を返す */
fun daysInMonth(
    year: Int,
    month: Int,
): Int = YearMonth(year, month).numberOfDays

/** 日付文字列から短縮曜日名を返す (e.g. "月", "火") */
fun dayOfWeekShort(dateStr: String): String = DAY_OF_WEEK_LABELS[parseDate(dateStr).dayOfWeek.toSundayIndex()]

/** 現在時刻を HH:MM 形式で返す (JST) */
fun currentTime(now: Instant = Clock.System.now()): String = jstNow(now).let { formatHHMM(it.hour, it.minute) }

/** 今日の日付を "M月D日(曜)" 形式で返す (JST) */
fun formattedToday(now: Instant = Clock.System.now()): String {
    val date = jstNow(now).date
    return "${date.month.number}月${date.day}日(${DAY_OF_WEEK_LABELS[date.dayOfWeek.toSundayIndex()]})"
}

/** 今日の年を返す (JST) */
fun currentYear(now: Instant = Clock.System.now()): String = jstNow(now).year.toString()

/** 今日が月内の第何週か返す (1-5)。日曜始まりで計算。 */
fun weekOfMonth(now: Instant = Clock.System.now()): Int = (jstNow(now).day + 6) / 7

/** 今日の曜日を 0(日)〜6(土) で返す */
fun dayOfWeekIndex(now: Instant = Clock.System.now()): Int = jstNow(now).dayOfWeek.toSundayIndex()

/** 明日の曜日を 0(日)〜6(土) で返す */
fun tomorrowDayOfWeekIndex(now: Instant = Clock.System.now()): Int =
    jstNow(now)
        .date
        .plus(1, DateTimeUnit.DAY)
        .dayOfWeek
        .toSundayIndex()

/** 明日が月内の第何週か返す (1-5)。日曜始まりで計算。 */
fun tomorrowWeekOfMonth(now: Instant = Clock.System.now()): Int = (jstNow(now).date.plus(1, DateTimeUnit.DAY).day + 6) / 7

/** ISO タイムスタンプを JST の HH:MM 形式に変換 */
fun toJstHHMM(iso: String): String = jstFromIso(iso).let { formatHHMM(it.hour, it.minute) }

/** ISO タイムスタンプから JST の時を取得 */
fun toJstHour(iso: String): String = jstFromIso(iso).hour.toString().padStart(2, '0')

/** ISO タイムスタンプから JST の分を取得 */
fun toJstMinute(iso: String): String = jstFromIso(iso).minute.toString().padStart(2, '0')

/** ISO タイムスタンプを JST の "M/D" 形式に変換 */
fun toJstMonthDay(iso: String): String = jstFromIso(iso).let { "${it.month.number}/${it.day}" }

/** "YYYY-MM-DD" 形式の日付文字列を "M月D日" 形式に変換 */
fun formatDueDate(dateStr: String): String = parseDate(dateStr).let { "${it.month.number}月${it.day}日" }

/**
 * 支払期日のグループ見出し。null（未設定）は「支払期日なし」。
 * 日付だけだと項目の入力日と誤読されるため、「支払期日:」プレフィックスを必ず付ける。
 */
fun dueDateGroupLabel(dueDate: String?): String = dueDate?.let { "支払期日: ${formatDueDate(it)}" } ?: "支払期日なし"

private fun deadlineToInstant(deadline: String): Instant {
    val date = parseDate(deadline)
    val time =
        if (deadline.length > 10) {
            val timePart = deadline.substring(11)
            LocalTime(timePart.substring(0, 2).toInt(), timePart.substring(3, 5).toInt())
        } else {
            LocalTime(23, 59, 59)
        }
    return LocalDateTime(date, time).toInstant(JST)
}

/**
 * 期限文字列("YYYY-MM-DD" or "YYYY-MM-DD HH:MM")から残り時間テキストを返す。
 * 1日以上: "あとX日"、1日以内: "あとX時間"、期限切れ: "期限切れ"
 */
fun remainingTime(
    deadline: String,
    now: Instant = Clock.System.now(),
): String {
    val diff = deadlineToInstant(deadline) - now
    if (diff <= Duration.ZERO) return "期限切れ"
    val hours = diff.inWholeHours
    if (hours < 24) return "あと${hours}時間"
    return "あと${diff.inWholeDays}日"
}
