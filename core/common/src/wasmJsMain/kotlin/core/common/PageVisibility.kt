@file:OptIn(ExperimentalWasmJsInterop::class)

package core.common

/**
 * ページが非表示→表示に切り替わったときに callback を呼ぶリスナーを登録する。
 * 戻り値のハンドルを [removePageVisibleListener] に渡して解除する。
 */
@JsFun(
    """(callback) => {
    const handler = () => { if (!document.hidden) callback(); };
    document.addEventListener("visibilitychange", handler);
    return handler;
}""",
)
external fun addPageVisibleListener(callback: () -> Unit): JsAny

/** [addPageVisibleListener] で登録したリスナーを解除する。 */
@JsFun("(handler) => document.removeEventListener('visibilitychange', handler)")
external fun removePageVisibleListener(handler: JsAny)
