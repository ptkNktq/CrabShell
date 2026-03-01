@file:OptIn(ExperimentalWasmJsInterop::class)

package core.ui.util

/** 今日の日付を YYYY-MM-DD 形式で返す */
@JsFun(
    """() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}""",
)
external fun todayDateJs(): JsString

/** 餌やり日付を YYYY-MM-DD 形式で返す（JST 5時を日付境界とする） */
@JsFun(
    """() => {
    const d = new Date();
    const jst = new Date(d.getTime() + 9 * 60 * 60 * 1000);
    if (jst.getUTCHours() < 5) {
        jst.setUTCDate(jst.getUTCDate() - 1);
    }
    const y = jst.getUTCFullYear();
    const m = String(jst.getUTCMonth() + 1).padStart(2, '0');
    const day = String(jst.getUTCDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}""",
)
external fun feedingDateJs(): JsString

/** 日付文字列を days 日ずらす */
@JsFun(
    """(dateStr, days) => {
    const d = new Date(dateStr + 'T00:00:00');
    d.setDate(d.getDate() + days);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}""",
)
external fun shiftDateJs(
    dateStr: JsString,
    days: Int,
): JsString

/** 指定月の1日の曜日を返す (0=Sun, 1=Mon, ..., 6=Sat) */
@JsFun(
    """(year, month) => {
    return new Date(year, month - 1, 1).getDay();
}""",
)
external fun firstDayOfWeekJs(
    year: Int,
    month: Int,
): Int

/** 指定月の日数を返す */
@JsFun(
    """(year, month) => {
    return new Date(year, month, 0).getDate();
}""",
)
external fun daysInMonthJs(
    year: Int,
    month: Int,
): Int

/** 日付文字列から短縮曜日名を返す (e.g. "月", "火") */
@JsFun(
    """(dateStr) => {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('ja-JP', { weekday: 'short' });
}""",
)
external fun dayOfWeekShortJs(dateStr: JsString): JsString

/** 現在時刻を HH:MM 形式で返す (JST) */
@JsFun(
    """() => {
    return new Date().toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Tokyo' });
}""",
)
external fun currentTimeJs(): JsString

/** 今日の日付を "M月D日（曜）" 形式で返す (JST) */
@JsFun(
    """() => {
    const d = new Date();
    const opts = { month: 'long', day: 'numeric', weekday: 'short', timeZone: 'Asia/Tokyo' };
    return d.toLocaleDateString('ja-JP', opts);
}""",
)
external fun formattedTodayJs(): JsString

/** 今日の年を返す (JST) */
@JsFun(
    """() => {
    return new Date().toLocaleDateString('ja-JP', { year: 'numeric', timeZone: 'Asia/Tokyo' });
}""",
)
external fun currentYearJs(): JsString

/** 今日が月内の第何週か返す (1-5)。日曜始まりで計算。 */
@JsFun(
    """() => {
    const d = new Date();
    return Math.ceil(d.getDate() / 7);
}""",
)
external fun weekOfMonthJs(): Int

/** 今日の曜日を 0(日)〜6(土) で返す */
@JsFun(
    """() => {
    return new Date().getDay();
}""",
)
external fun dayOfWeekIndexJs(): Int

/** 明日の曜日を 0(日)〜6(土) で返す */
@JsFun(
    """() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.getDay();
}""",
)
external fun tomorrowDayOfWeekIndexJs(): Int

/** 明日が月内の第何週か返す (1-5)。日曜始まりで計算。 */
@JsFun(
    """() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return Math.ceil(d.getDate() / 7);
}""",
)
external fun tomorrowWeekOfMonthJs(): Int

/** ISO タイムスタンプを JST の HH:MM 形式に変換 */
@JsFun(
    """(iso) => {
    const d = new Date(iso);
    return d.toLocaleTimeString('ja-JP', {
        hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Tokyo',
    });
}""",
)
external fun toJstHHMM(iso: JsString): JsString

/** ISO タイムスタンプから JST の時を取得 */
@JsFun(
    """(iso) => {
    const d = new Date(iso);
    return d.toLocaleString('en-US', { hour: '2-digit', hour12: false, timeZone: 'Asia/Tokyo' });
}""",
)
external fun toJstHour(iso: JsString): JsString

/** ISO タイムスタンプから JST の分を取得 */
@JsFun(
    """(iso) => {
    const d = new Date(iso);
    return d.toLocaleString('en-US', { minute: '2-digit', timeZone: 'Asia/Tokyo' });
}""",
)
external fun toJstMinute(iso: JsString): JsString

/**
 * 期限文字列("YYYY-MM-DD" or "YYYY-MM-DD HH:MM")から残り時間テキストを返す。
 * 1日以上: "あとX日"、1日以内: "あとX時間"、期限切れ: "期限切れ"
 */
@JsFun(
    """(deadline) => {
    const now = new Date();
    let target;
    if (deadline.length > 10) {
        const parts = deadline.split(' ');
        target = new Date(parts[0] + 'T' + parts[1] + ':00');
    } else {
        target = new Date(deadline + 'T23:59:59');
    }
    const diff = target.getTime() - now.getTime();
    if (diff <= 0) return '期限切れ';
    const hours = Math.floor(diff / (1000 * 60 * 60));
    if (hours < 24) return 'あと' + hours + '時間';
    const days = Math.floor(hours / 24);
    return 'あと' + days + '日';
}""",
)
external fun remainingTimeJs(deadline: JsString): JsString
