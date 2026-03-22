package core.common

/**
 * アプリケーション共通の軽量ロガー。
 * wasmJs では console.log/warn/error に出力（開発環境のみ）。
 * JVM では no-op（サーバーは SLF4J + Logback を使用）。
 */
object AppLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun d(
        tag: String,
        message: String,
    ) = log(Level.DEBUG, tag, message)

    fun i(
        tag: String,
        message: String,
    ) = log(Level.INFO, tag, message)

    fun w(
        tag: String,
        message: String,
    ) = log(Level.WARN, tag, message)

    fun e(
        tag: String,
        message: String,
    ) = log(Level.ERROR, tag, message)

    private fun log(
        level: Level,
        tag: String,
        message: String,
    ) {
        platformLog(level, tag, message)
    }
}

internal expect fun platformLog(
    level: AppLogger.Level,
    tag: String,
    message: String,
)
