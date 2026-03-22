package core.common

internal actual fun platformLog(
    level: AppLogger.Level,
    tag: String,
    message: String,
) {
    // JVM (server) では SLF4J + Logback を直接使用するため no-op
}
