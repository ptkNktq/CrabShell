package server.quest

import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Quest
import model.QuestCategory
import model.QuestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebhookPayloadTest {
    private val service = WebhookService(firestore = mockk<Firestore>())

    private val sampleQuest =
        Quest(
            id = "q1",
            title = "部屋を掃除する",
            description = "リビングと寝室の掃除をお願いします",
            category = QuestCategory.Cleaning,
            rewardPoints = 50,
            creatorUid = "user1",
            creatorName = "太郎",
            status = QuestStatus.Open,
            createdAt = "2024-07-01T00:00:00Z",
        )

    private fun parseJson(jsonString: String): JsonObject = Json.parseToJsonElement(jsonString).jsonObject

    // --- Discord ペイロード ---

    @Test
    fun discordPayloadStructure() {
        val json =
            parseJson(
                service.buildPayload("https://discord.com/api/webhooks/12345/token", "quest_created", sampleQuest),
            )

        val embeds = json["embeds"]
        assertIs<JsonArray>(embeds)
        assertEquals(1, embeds.size)

        val embed = embeds[0].jsonObject
        assertTrue(embed["title"]!!.jsonPrimitive.content.contains(sampleQuest.title))
        assertEquals(sampleQuest.description, embed["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun discordPayloadFields() {
        val json =
            parseJson(
                service.buildPayload("https://discord.com/api/webhooks/12345/token", "quest_created", sampleQuest),
            )

        val fields = json["embeds"]!!.jsonArray[0].jsonObject["fields"]!!.jsonArray
        assertEquals(2, fields.size)

        val rewardField = fields.first { it.jsonObject["name"]!!.jsonPrimitive.content == "報酬" }.jsonObject
        assertEquals("50pt", rewardField["value"]!!.jsonPrimitive.content)

        val creatorField = fields.first { it.jsonObject["name"]!!.jsonPrimitive.content == "依頼者" }.jsonObject
        assertEquals("太郎", creatorField["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun discordAppUrlAlsoProducesDiscordPayload() {
        val json =
            parseJson(
                service.buildPayload("https://discordapp.com/api/webhooks/12345/token", "quest_created", sampleQuest),
            )
        // Discord ペイロードは embeds を持つ
        assertIs<JsonArray>(json["embeds"])
    }

    // --- Slack ペイロード ---

    @Test
    fun slackPayloadText() {
        val json =
            parseJson(
                service.buildPayload("https://hooks.slack.com/services/T00/B00/xxx", "quest_created", sampleQuest),
            )

        val text = json["text"]!!.jsonPrimitive.content
        assertTrue(text.contains(sampleQuest.title))
        assertTrue(text.contains(sampleQuest.description))
        assertTrue(text.contains("50pt"))
        assertTrue(text.contains("太郎"))
    }

    // --- Generic ペイロード ---

    @Test
    fun genericPayloadEventAndTimestamp() {
        val json =
            parseJson(
                service.buildPayload(
                    "https://example.com/webhook",
                    "quest_created",
                    sampleQuest,
                    timestamp = "2024-07-01T12:00:00Z",
                ),
            )

        assertEquals("quest_created", json["event"]!!.jsonPrimitive.content)
        assertEquals("2024-07-01T12:00:00Z", json["timestamp"]!!.jsonPrimitive.content)
    }

    @Test
    fun genericPayloadQuestData() {
        val json =
            parseJson(
                service.buildPayload(
                    "https://example.com/webhook",
                    "quest_created",
                    sampleQuest,
                    timestamp = "2024-07-01T12:00:00Z",
                ),
            )

        val quest = json["quest"]!!.jsonObject
        assertEquals(sampleQuest.title, quest["title"]!!.jsonPrimitive.content)
        assertEquals(sampleQuest.description, quest["description"]!!.jsonPrimitive.content)
        assertEquals(sampleQuest.rewardPoints, quest["rewardPoints"]!!.jsonPrimitive.int)
        assertEquals(sampleQuest.creatorName, quest["creatorName"]!!.jsonPrimitive.content)
    }

    // --- URL によるサービス判別 ---

    @Test
    fun caseInsensitiveUrlDetection() {
        val json =
            parseJson(
                service.buildPayload("https://DISCORD.COM/API/WEBHOOKS/12345/token", "quest_created", sampleQuest),
            )
        // Discord ペイロードは embeds を持つ
        assertIs<JsonArray>(json["embeds"])
    }

    // --- eventPrefix ---

    @Test
    fun questCreatedEventContainsPrefix() {
        val json =
            parseJson(
                service.buildPayload("https://discord.com/api/webhooks/12345/token", "quest_created", sampleQuest),
            )
        val title =
            json["embeds"]!!
                .jsonArray[0]
                .jsonObject["title"]!!
                .jsonPrimitive.content
        assertTrue(title.contains("新しいクエスト"), "title should contain '新しいクエスト': $title")
    }

    @Test
    fun questVerifiedEventContainsPrefix() {
        val json =
            parseJson(
                service.buildPayload("https://discord.com/api/webhooks/12345/token", "quest_verified", sampleQuest),
            )
        val title =
            json["embeds"]!!
                .jsonArray[0]
                .jsonObject["title"]!!
                .jsonPrimitive.content
        assertTrue(title.contains("クエスト達成"), "title should contain 'クエスト達成': $title")
    }
}
