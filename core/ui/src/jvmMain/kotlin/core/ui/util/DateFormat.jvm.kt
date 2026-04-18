package core.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val JST = ZoneId.of("Asia/Tokyo")
private val FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

actual fun formatIsoToJst(iso: String): String =
    try {
        Instant.parse(iso).atZone(JST).format(FORMATTER)
    } catch (_: Exception) {
        iso
    }
