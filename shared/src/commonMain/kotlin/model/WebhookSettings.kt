package model

/**
 * Webhook 設定モデルの共通契約。
 *
 * Money / Payment / Quest の各 Webhook 設定が最低限共有するフィールドを定義し、
 * サーバー側の `AbstractWebhookService` で「enabled かつ url が空でないか」の
 * 共通判定を型安全に行えるようにする。
 *
 * シリアライズには関与しないため `@Serializable` は付けない。
 * 実装側の `@Serializable data class` が宣言済みプロパティをそのまま直列化する。
 */
interface WebhookSettings {
    val url: String
    val enabled: Boolean
}
