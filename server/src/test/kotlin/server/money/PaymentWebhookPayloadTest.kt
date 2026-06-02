package server.money

import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentWebhookPayloadTest {
    private val service = PaymentWebhookService(firestore = mockk<Firestore>())

    private fun parseJson(jsonString: String): JsonObject = Json.parseToJsonElement(jsonString).jsonObject

    // --- Discord ペイロード ---

    @Test
    fun discordPayloadStructure() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "@everyone",
                    yearMonth = "2026-04",
                    payerName = "山田太郎",
                    amount = 12345L,
                    dashboardUrl = "https://example.com/",
                ),
            )

        val embeds = json["embeds"]
        assertIs<JsonArray>(embeds)
        assertEquals(1, embeds.size)

        val embed = embeds[0].jsonObject
        assertEquals("入金あり", embed["title"]!!.jsonPrimitive.content)
        val description = embed["description"]!!.jsonPrimitive.content
        assertTrue(description.contains("2026年04月"), "description should contain '2026年04月': $description")
        assertTrue(description.contains("山田太郎"), "description should contain payerName: $description")
        assertTrue(description.contains("12,345"), "description should contain formatted amount: $description")
    }

    @Test
    fun discordPayloadFieldsIncludePayerAmountAndYearMonth() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                ),
            )

        val fields = json["embeds"]!!.jsonArray[0].jsonObject["fields"]!!.jsonArray
        val names = fields.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("支払者", "金額", "対象月"), names)
    }

    @Test
    fun discordPayloadIncludesMessageAsContent() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "@everyone",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        assertEquals("@everyone", json["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun discordPayloadOmitsContentWhenMessageBlank() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        assertNull(json["content"])
    }

    // --- Slack ペイロード ---

    @Test
    fun slackPayloadIncludesMessagePayerAmountAndDashboard() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://hooks.slack.com/services/T00/B00/xxx",
                    message = "入金通知",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = "https://example.com/",
                ),
            )

        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("入金通知"), text)
        assertTrue(text.contains("Alice"), text)
        assertTrue(text.contains("5,000"), text)
        assertTrue(text.contains("2026年04月"), text)
        assertTrue(text.contains("<https://example.com/|ダッシュボードを開く>"), text)
    }

    @Test
    fun slackPayloadOmitsLinkWhenDashboardUrlNull() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://hooks.slack.com/services/T00/B00/xxx",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(!text.contains("ダッシュボードを開く"), text)
    }

    // --- 汎用ペイロード ---

    @Test
    fun genericPayloadFields() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://example.com/webhook",
                    message = "入金通知",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = "https://example.com/",
                ),
            )

        assertEquals("payment_recorded", json["event"]!!.jsonPrimitive.content)
        assertEquals("2026-04", json["yearMonth"]!!.jsonPrimitive.content)
        assertEquals("Alice", json["payerName"]!!.jsonPrimitive.content)
        assertEquals(5000L, json["amount"]!!.jsonPrimitive.content.toLong())
        assertEquals("入金通知", json["message"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/", json["dashboardUrl"]!!.jsonPrimitive.content)
    }

    @Test
    fun genericPayloadOmitsDashboardUrlWhenNull() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://example.com/webhook",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        assertNull(json["dashboardUrl"])
    }

    // --- displayName 経由のメンションインジェクション対策 ---

    @Test
    fun discordPayerNameWithEveryoneIsSanitized() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "@everyone",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        val description =
            json["embeds"]!!
                .jsonArray[0]
                .jsonObject["description"]!!
                .jsonPrimitive.content
        assertTrue(!description.contains("@everyone"), "description should not contain raw @everyone: $description")
    }

    @Test
    fun discordPayerNameWithUserMentionIsSanitized() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "<@123456789>",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        val payerField =
            json["embeds"]!!
                .jsonArray[0]
                .jsonObject["fields"]!!
                .jsonArray
                .first { it.jsonObject["name"]!!.jsonPrimitive.content == "支払者" }
                .jsonObject["value"]!!
                .jsonPrimitive.content
        assertTrue(!payerField.contains("<@1"), "支払者 field should not contain raw user mention: $payerField")
    }

    @Test
    fun discordAdminMessageMentionIsPreserved() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "<@123456789> 入金確認お願い",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        // admin が設定する message は意図的なメンションのため温存される
        assertEquals("<@123456789> 入金確認お願い", json["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun slackPayerNameWithChannelIsSanitized() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://hooks.slack.com/services/T00/B00/xxx",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "<!channel>",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(!text.contains("<!channel>"), "text should not contain raw <!channel>: $text")
    }

    @Test
    fun slackPayerNameWithUserMentionIsSanitized() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://hooks.slack.com/services/T00/B00/xxx",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "<@U12345>",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(!text.contains("<@U"), "text should not contain raw user mention: $text")
    }

    // --- URL によるサービス判別 ---

    @Test
    fun caseInsensitiveDiscordDetection() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://DISCORD.COM/API/WEBHOOKS/12345/token",
                    message = "",
                    yearMonth = "2026-04",
                    payerName = "Alice",
                    amount = 5000L,
                    dashboardUrl = null,
                ),
            )
        assertIs<JsonArray>(json["embeds"])
    }
}
