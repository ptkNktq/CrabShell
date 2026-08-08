package server.util

import config.HttpTimeouts
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

/**
 * サーバーから外部へ送信する HttpClient 全般（Webhook 送信、Gemini 等の外部 API 呼び出し）の
 * 既定生成関数。リクエストタイムアウトは [HttpTimeouts] で全体統一する。
 *
 * Money / Payment / Quest の Webhook サービス（[AbstractWebhookService]）、Feeding / Garbage / Money
 * 期日リマインダーの通知サービス、Gemini API クライアントがすべてこれを経由する。
 * 追加のプラグイン（ContentNegotiation 等）が必要な場合は [additionalConfig] で差し込む。
 */
fun defaultHttpClient(additionalConfig: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeouts.DEFAULT_REQUEST_TIMEOUT_MILLIS
        }
        additionalConfig()
    }
