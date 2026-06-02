package server.util

/**
 * Webhook 送信先（Discord / Slack）のメンションインジェクション対策。
 *
 * 信頼境界を超えてユーザー入力（displayName, quest.title 等）を Discord embed / Slack text
 * に埋め込む箇所で、メンション系記法を無効化する。
 *
 * Webhook 設定の admin 配信メッセージ（PaymentWebhookSettings.message 等）はサニタイズ
 * 対象外のため、admin が `<@USER_ID>` 等のユーザー指定メンションを意図的に埋め込む運用は
 * 維持される。サニタイズはあくまで「不特定多数のユーザーが自由に書き換えられる値」が
 * Discord/Slack に渡る経路に限定する。
 *
 * **方式の選択**:
 * - **Discord**: 公式 `allowed_mentions` パラメータも存在するが、payload 全体に作用する
 *   ため admin message の `<@USER_ID>` も同時に無効化されてしまう。フィールド単位で適用
 *   できる ZWS (U+200B) 分断方式を採用。
 * - **Slack**: 公式が推奨する HTML エンティティ化方式 (`<` → `&lt;`, `>` → `&gt;`,
 *   `&` → `&amp;`) を採用。フィールド単位で適用可能で、将来 Slack が新しいメンション
 *   記法を追加しても自動的に無効化される。
 */

internal const val ZWS = "​"

/**
 * Discord 用にユーザー入力をサニタイズする。ZWS 分断方式。
 *
 * 無効化対象:
 * - `@everyone`, `@here`（一斉メンション）
 * - `<@USER_ID>`, `<@!USER_ID>`, `<@&ROLE_ID>`（ユーザー / ロールメンション）
 * - `<#CHANNEL_ID>`（チャンネルメンション）
 */
fun sanitizeForDiscord(text: String): String =
    text
        // 置換順序依存: 先に `@everyone` / `@here` を分断してから `<@` の汎用置換を行うことで
        // `<@everyone` のような不正記法も正しく無効化される。順序を入れ替えると挙動が変わるため注意。
        .replace("@everyone", "@${ZWS}everyone")
        .replace("@here", "@${ZWS}here")
        .replace("<@", "<$ZWS@")
        .replace("<#", "<$ZWS#")

/**
 * Slack 用にユーザー入力をサニタイズする。HTML エンティティ化方式。
 *
 * Slack のメンション・チャンネル参照・特殊コマンドはすべて `<` で始まる記法のため、
 * `<` / `>` / `&` を HTML エンティティ化することで、現存・将来の全ての記法を一括無効化する。
 * `<URL|text>` 形式のリンクや `<mailto:>` も同様に無効化される（ユーザー入力経由で勝手に
 * リンクが生成されるのを防ぐ副次効果あり）。
 *
 * 置換順序: `&` を最初に処理しないと `<` → `&lt;` → `&amp;lt;` と二重エンコードされる。
 */
fun sanitizeForSlack(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
