package server.util

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import model.WebhookSettings
import org.slf4j.LoggerFactory

/**
 * `settings/{documentName}` ドキュメントの `webhook` map に設定を保存する Webhook サービスの共通基底。
 *
 * Money / Payment / Quest で重複していた以下を集約する:
 * - `getSettings` / `updateSettings`（Firestore I/O）
 * - logger / scope / HttpClient の初期化
 *
 * 設定モデル ↔ Firestore map の変換だけをサブクラスが [defaultSettings] / [fromWebhookMap] /
 * [toWebhookMap] で与え、ドメイン固有の通知メソッドはサブクラス側に実装する。
 *
 * キャッシュは持たず毎回 Firestore から読む。webhook 設定の read 頻度は低く、
 * in-memory キャッシュは将来のマルチインスタンス運用で設定変更が反映されない
 * stale リスクを招くため、即時反映性を優先する。
 */
abstract class AbstractWebhookService<S : WebhookSettings>(
    private val firestore: Firestore,
    private val documentName: String,
    protected val client: HttpClient = defaultWebhookClient(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    protected val logger = LoggerFactory.getLogger(this::class.java)
    protected val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val settingsDoc get() = firestore.collection("settings").document(documentName)

    /** 設定が未保存のときに返すデフォルト値。 */
    protected abstract fun defaultSettings(): S

    /** Firestore の `webhook` map を設定モデルへ変換する。 */
    protected abstract fun fromWebhookMap(map: Map<String, Any?>): S

    /** 設定モデルを Firestore 保存用 map へ変換する。 */
    protected abstract fun toWebhookMap(settings: S): Map<String, Any?>

    suspend fun getSettings(): S {
        val doc = settingsDoc.get().await()
        if (!doc.exists()) return defaultSettings()

        @Suppress("UNCHECKED_CAST")
        val webhook = doc.data?.get("webhook") as? Map<String, Any?> ?: return defaultSettings()
        return fromWebhookMap(webhook)
    }

    /** 設定を保存し、保存値をそのまま返す（merge は webhook map を全上書きするため再 GET 不要）。 */
    suspend fun updateSettings(settings: S): S {
        settingsDoc
            .set(mapOf("webhook" to toWebhookMap(settings)), SetOptions.merge())
            .await()
        return settings
    }

    companion object {
        fun defaultWebhookClient(): HttpClient =
            HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                }
            }
    }
}
