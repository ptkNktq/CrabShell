package server.money

import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import server.money.MoneyWebhookService.Companion.formatYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyWebhookPayloadTest {
    private val service = MoneyWebhookService(firestore = mockk<Firestore>())

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
                    dashboardUrl = "https://example.com/",
                ),
            )

        val embeds = json["embeds"]
        assertIs<JsonArray>(embeds)
        assertEquals(1, embeds.size)

        val embed = embeds[0].jsonObject
        assertEquals("支払額確定", embed["title"]!!.jsonPrimitive.content)
        val description = embed["description"]!!.jsonPrimitive.content
        assertTrue(description.contains("2026年4月"), "description should contain '2026年4月': $description")
    }

    @Test
    fun discordPayloadIncludesMessageAsContent() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discord.com/api/webhooks/12345/token",
                    message = "@everyone",
                    yearMonth = "2026-04",
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
                    dashboardUrl = null,
                ),
            )

        // 空メッセージ時は content フィールドを省略する（@Serializable のデフォルト null + encodeDefaults=false）
        assertNull(json["content"])
    }

    @Test
    fun discordAppUrlAlsoProducesDiscordPayload() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://discordapp.com/api/webhooks/12345/token",
                    message = "",
                    yearMonth = "2026-04",
                    dashboardUrl = null,
                ),
            )
        assertIs<JsonArray>(json["embeds"])
    }

    // --- Slack ペイロード ---

    @Test
    fun slackPayloadTextWithMessageAndDashboard() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://hooks.slack.com/services/T00/B00/xxx",
                    message = "確定しました",
                    yearMonth = "2026-04",
                    dashboardUrl = "https://example.com/",
                ),
            )

        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("確定しました"), "text should contain message: $text")
        assertTrue(text.contains("2026年4月"), "text should contain '2026年4月': $text")
        assertTrue(
            text.contains("<https://example.com/|ダッシュボードを開く>"),
            "text should contain Slack-style link: $text",
        )
    }

    @Test
    fun slackPayloadOmitsLinkWhenDashboardUrlNull() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://hooks.slack.com/services/T00/B00/xxx",
                    message = "",
                    yearMonth = "2026-04",
                    dashboardUrl = null,
                ),
            )

        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("2026年4月"), "text should contain year-month: $text")
        assertTrue(!text.contains("ダッシュボードを開く"), "text should not contain dashboard link: $text")
    }

    // --- Generic ペイロード ---

    @Test
    fun genericPayloadFields() {
        val json =
            parseJson(
                service.buildPayload(
                    url = "https://example.com/webhook",
                    message = "確定しました",
                    yearMonth = "2026-04",
                    dashboardUrl = "https://example.com/",
                ),
            )

        assertEquals("money_status_confirmed", json["event"]!!.jsonPrimitive.content)
        assertEquals("2026-04", json["yearMonth"]!!.jsonPrimitive.content)
        assertEquals("確定しました", json["message"]!!.jsonPrimitive.content)
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
                    dashboardUrl = null,
                ),
            )
        assertNull(json["dashboardUrl"])
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
                    dashboardUrl = null,
                ),
            )
        assertIs<JsonArray>(json["embeds"])
    }

    // --- formatYearMonth ---

    @Test
    fun formatYearMonthValidInput() {
        assertEquals("2026年4月", formatYearMonth("2026-04"))
        assertEquals("2026年12月", formatYearMonth("2026-12"))
    }

    @Test
    fun formatYearMonthReturnsInputOnInvalidFormat() {
        assertEquals("abc", formatYearMonth("abc"))
        assertEquals("2026", formatYearMonth("2026"))
        assertEquals("2026-04-01", formatYearMonth("2026-04-01"))
        assertEquals("2026-XX", formatYearMonth("2026-XX"))
    }
}
