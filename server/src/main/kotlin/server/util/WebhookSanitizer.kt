package server.util

/**
 * Webhook 送信先（Discord / Slack）のメンションインジェクション対策。
 *
 * 信頼境界を超えてユーザー入力（displayName, quest.title 等）を Discord embed / Slack text
 * に埋め込む箇所で、メンション系記法をゼロ幅スペースで分断し無効化する。
 *
 * Webhook 設定の admin 配信メッセージ（PaymentWebhookSettings.message 等）はサニタイズ
 * 対象外のため、admin が `<@USER_ID>` 等のユーザー指定メンションを意図的に埋め込む運用は
 * 維持される。サニタイズはあくまで「不特定多数のユーザーが自由に書き換えられる値」が
 * Discord/Slack に渡る経路に限定する。
 *
 * **設計判断**: Discord の `allowed_mentions` パラメータや Slack の HTML エンティティ化
 * (`<` → `&lt;`) も無効化手段として存在するが、いずれも payload 全体に作用するため
 * admin message 内の `<@USER_ID>` も同時に無効化されてしまう。本プロジェクトは
 * 「ユーザー入力フィールドのみサニタイズ・admin message は素通し」という要件のため、
 * フィールド単位で適用できる ZWS 分断方式を採用している。
 */

internal const val ZWS = "​"

/**
 * Discord 用にユーザー入力をサニタイズする。
 *
 * 無効化対象:
 * - `@everyone`, `@here`（一斉メンション）
 * - `<@USER_ID>`, `<@!USER_ID>`, `<@&ROLE_ID>`（ユーザー / ロールメンション）
 * - `<#CHANNEL_ID>`（チャンネルメンション）
 */
fun sanitizeForDiscord(text: String): String =
    text
        .replace("@everyone", "@${ZWS}everyone")
        .replace("@here", "@${ZWS}here")
        .replace("<@", "<$ZWS@")
        .replace("<#", "<$ZWS#")

/**
 * Slack 用にユーザー入力をサニタイズする。
 *
 * 無効化対象:
 * - `<!channel>`, `<!here>`, `<!everyone>`, `<!subteam^ID>`（特殊メンション・ユーザーグループ）
 * - `<@USER_ID>`（ユーザーメンション）
 * - `<#CHANNEL_ID>`（チャンネルメンション）
 */
fun sanitizeForSlack(text: String): String =
    text
        .replace("<!", "<$ZWS!")
        .replace("<@", "<$ZWS@")
        .replace("<#", "<$ZWS#")
