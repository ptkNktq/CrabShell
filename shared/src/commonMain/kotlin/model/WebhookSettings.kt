package model

/**
 * Webhook 設定モデルの共通契約。
 *
 * Money / Payment / Quest の各 Webhook 設定が共通して持つフィールド（url / enabled）を定義し、
 * `AbstractWebhookService<S : WebhookSettings>` の型境界として settings 種別を制約する。
 *
 * シリアライズには関与しないため `@Serializable` は付けない。
 * 実装側の `@Serializable data class` が宣言済みプロパティをそのまま直列化する。
 */
interface WebhookSettings {
    val url: String
    val enabled: Boolean
}
