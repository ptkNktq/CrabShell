package config

/**
 * アプリ全体で使う HTTP リクエストタイムアウトの既定値（ミリ秒）。
 *
 * サーバー側（Webhook 送信・Gemini API 呼び出し）とクライアント側（API クライアント）の
 * 両方の HttpClient 生成箇所から参照し、タイムアウト値を1箇所に統一する。
 * :shared は server（JVM）・core:network（wasmJs）の双方から参照できる唯一の共通モジュールのため、
 * ここに置く。
 */
object HttpTimeouts {
    const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 10_000L
}
