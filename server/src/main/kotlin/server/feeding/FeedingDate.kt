package server.feeding

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private val JST = ZoneId.of("Asia/Tokyo")

/** 給餌日付が切り替わる時刻（JST の時）。これより前は前日の給餌として扱う */
private const val FEEDING_DAY_START_HOUR = 5

/** JST 5:00 AM を日付境界とする給餌日付（YYYY-MM-DD）を算出する */
fun feedingDate(jstNow: ZonedDateTime): String {
    val adjusted = if (jstNow.hour < FEEDING_DAY_START_HOUR) jstNow.minusDays(1) else jstNow
    return adjusted.toLocalDate().toString()
}

/** [now] 時点の給餌日付（YYYY-MM-DD）を算出する */
fun feedingDate(now: Instant): String = feedingDate(now.atZone(JST))
