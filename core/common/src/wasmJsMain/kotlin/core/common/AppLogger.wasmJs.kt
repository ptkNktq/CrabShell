package core.common

internal actual fun platformLog(
    level: AppLogger.Level,
    tag: String,
    message: String,
) {
    if (!isDevEnvironment) return

    val formatted = "[$tag] $message"
    when (level) {
        AppLogger.Level.DEBUG -> console.log(formatted.toJsString())
        AppLogger.Level.INFO -> console.info(formatted.toJsString())
        AppLogger.Level.WARN -> console.warn(formatted.toJsString())
        AppLogger.Level.ERROR -> console.error(formatted.toJsString())
    }
}

@Suppress("ktlint:standard:class-naming")
private external object console {
    fun log(message: JsString)

    fun info(message: JsString)

    fun warn(message: JsString)

    fun error(message: JsString)
}
