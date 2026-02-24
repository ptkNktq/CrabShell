package server.util

import io.ktor.http.Parameters
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException

/**
 * enum パラメータを取得。未指定なら [MissingRequestParameterException]、不正値なら [ParameterConversionException] を throw。
 *
 * String のまま取得する場合は型変換不要の [io.ktor.server.util.getOrFail] を使うこと。
 */
inline fun <reified T : Enum<T>> Parameters.getEnumOrFail(name: String): T {
    val raw = this[name] ?: throw MissingRequestParameterException(name)
    return try {
        enumValueOf<T>(raw.uppercase())
    } catch (_: IllegalArgumentException) {
        throw ParameterConversionException(name, T::class.simpleName ?: "Enum", IllegalArgumentException("Invalid value: $raw"))
    }
}
