@file:OptIn(ExperimentalWasmJsInterop::class)

package core.ui.util

/** 今日の日付を YYYY-MM-DD 形式で返す */
@JsFun("""() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}""")
external fun todayDateJs(): JsString

/** 日付文字列を days 日ずらす */
@JsFun("""(dateStr, days) => {
    const d = new Date(dateStr + 'T00:00:00');
    d.setDate(d.getDate() + days);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}""")
external fun shiftDateJs(dateStr: JsString, days: Int): JsString

/** 指定月の1日の曜日を返す (0=Sun, 1=Mon, ..., 6=Sat) */
@JsFun("""(year, month) => {
    return new Date(year, month - 1, 1).getDay();
}""")
external fun firstDayOfWeekJs(year: Int, month: Int): Int

/** 指定月の日数を返す */
@JsFun("""(year, month) => {
    return new Date(year, month, 0).getDate();
}""")
external fun daysInMonthJs(year: Int, month: Int): Int

/** 日付文字列から短縮曜日名を返す (e.g. "月", "火") */
@JsFun("""(dateStr) => {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('ja-JP', { weekday: 'short' });
}""")
external fun dayOfWeekShortJs(dateStr: JsString): JsString
