package server.quest

import com.google.cloud.firestore.Firestore
import io.mockk.mockk
import model.Quest
import model.QuestCategory
import model.QuestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    // --- detectService ---

    @Test
    fun detectServiceDiscordUrl() {
        assertEquals(
            WebhookService.Service.DISCORD,
            service.detectService("https://discord.com/api/webhooks/12345/token"),
        )
    }

    @Test
    fun detectServiceDiscordAppUrl() {
        assertEquals(
            WebhookService.Service.DISCORD,
            service.detectService("https://discordapp.com/api/webhooks/12345/token"),
        )
    }

    @Test
    fun detectServiceSlackUrl() {
        assertEquals(
            WebhookService.Service.SLACK,
            service.detectService("https://hooks.slack.com/services/T00/B00/xxx"),
        )
    }

    @Test
    fun detectServiceGenericUrl() {
        assertEquals(
            WebhookService.Service.GENERIC,
            service.detectService("https://example.com/webhook"),
        )
    }

    @Test
    fun detectServiceCaseInsensitive() {
        assertEquals(
            WebhookService.Service.DISCORD,
            service.detectService("https://DISCORD.COM/API/WEBHOOKS/12345/token"),
        )
    }

    // --- eventPrefix ---

    @Test
    fun eventPrefixQuestCreated() {
        val prefix = service.eventPrefix("quest_created")
        assertTrue(prefix.contains("新しいクエスト"), "prefix should contain '新しいクエスト': $prefix")
    }

    @Test
    fun eventPrefixQuestVerified() {
        val prefix = service.eventPrefix("quest_verified")
        assertTrue(prefix.contains("クエスト達成"), "prefix should contain 'クエスト達成': $prefix")
    }

    @Test
    fun eventPrefixUnknownReturnsAsIs() {
        assertEquals("some_event", service.eventPrefix("some_event"))
    }

    // --- buildDiscordPayload ---

    @Test
    fun buildDiscordPayloadStructure() {
        val payload = service.buildDiscordPayload("quest_created", sampleQuest)
        assertEquals(1, payload.embeds.size)
        val embed = payload.embeds.first()
        assertTrue(embed.title.contains(sampleQuest.title))
        assertEquals(sampleQuest.description, embed.description)
        assertEquals(WebhookService.DISCORD_EMBED_COLOR, embed.color)
    }

    @Test
    fun buildDiscordPayloadFields() {
        val payload = service.buildDiscordPayload("quest_created", sampleQuest)
        val fields = payload.embeds.first().fields
        assertEquals(2, fields.size)

        val rewardField = fields.find { it.name == "報酬" }
        assertNotNull(rewardField)
        assertEquals("50pt", rewardField.value)
        assertTrue(rewardField.inline)

        val creatorField = fields.find { it.name == "依頼者" }
        assertNotNull(creatorField)
        assertEquals("太郎", creatorField.value)
    }

    // --- buildSlackPayload ---

    @Test
    fun buildSlackPayloadText() {
        val payload = service.buildSlackPayload("quest_created", sampleQuest)
        assertTrue(payload.text.contains(sampleQuest.title))
        assertTrue(payload.text.contains(sampleQuest.description))
        assertTrue(payload.text.contains("50pt"))
        assertTrue(payload.text.contains("太郎"))
    }

    // --- buildGenericPayload ---

    @Test
    fun buildGenericPayloadEvent() {
        val payload = service.buildGenericPayload("quest_created", sampleQuest, timestamp = "2024-07-01T12:00:00Z")
        assertEquals("quest_created", payload.event)
        assertEquals("2024-07-01T12:00:00Z", payload.timestamp)
    }

    @Test
    fun buildGenericPayloadQuestData() {
        val payload = service.buildGenericPayload("quest_created", sampleQuest, timestamp = "2024-07-01T12:00:00Z")
        assertEquals(sampleQuest.title, payload.quest.title)
        assertEquals(sampleQuest.description, payload.quest.description)
        assertEquals(sampleQuest.rewardPoints, payload.quest.rewardPoints)
        assertEquals(sampleQuest.creatorName, payload.quest.creatorName)
    }
}
