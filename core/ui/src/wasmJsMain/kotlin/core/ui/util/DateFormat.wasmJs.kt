@file:OptIn(ExperimentalWasmJsInterop::class)

package core.ui.util

@JsFun(
    """(iso) => {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    const opts = {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', hour12: false,
        timeZone: 'Asia/Tokyo',
    };
    return new Intl.DateTimeFormat('ja-JP', opts).format(d).replace(',', '');
}""",
)
private external fun formatIsoToJstJs(iso: JsString): JsString

actual fun formatIsoToJst(iso: String): String = formatIsoToJstJs(iso.toJsString()).toString()
