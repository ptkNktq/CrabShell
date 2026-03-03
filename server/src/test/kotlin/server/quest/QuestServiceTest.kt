package server.quest

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import model.CreateQuestRequest
import model.Quest
import model.QuestCategory
import model.QuestStatus
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QuestServiceTest {
    private val questRepository = mockk<QuestRepository>()
    private val pointRepository = mockk<PointRepository>()
    private val webhookService = mockk<WebhookService>(relaxed = true)
    private val service = QuestService(questRepository, pointRepository, webhookService)

    // --- listQuests ---

    @Test
    fun listQuestsExpiredQuestGetsStatusUpdated() =
        runTest {
            val now = LocalDate.of(2024, 7, 15)
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Open",
                    "deadline" to "2024-07-14",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuests(null) } returns listOf("q1" to questData)
            coEvery { questRepository.updateQuest("q1", any()) } just runs

            val result = service.listQuests(null, now)

            assertEquals(1, result.size)
            assertEquals(QuestStatus.Expired, result.first().status)
            coVerify { questRepository.updateQuest("q1", mapOf("status" to "Expired")) }
        }

    @Test
    fun listQuestsWithStatusFilter() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "完了済み",
                    "category" to "Cleaning",
                    "rewardPoints" to 30,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Verified",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuests("Verified") } returns listOf("q1" to questData)

            val result = service.listQuests("Verified")

            assertEquals(1, result.size)
            assertEquals(QuestStatus.Verified, result.first().status)
        }

    // --- createQuest ---

    @Test
    fun createQuestBelowLimitReturnsSuccess() =
        runTest {
            val request =
                CreateQuestRequest(
                    title = "買い物",
                    description = "牛乳を買ってきて",
                    category = QuestCategory.Errand,
                    rewardPoints = 20,
                    deadline = "2024-07-20",
                )
            val now = Instant.parse("2024-07-15T10:00:00Z")

            coEvery { questRepository.countActiveQuests() } returns 5
            coEvery { questRepository.createQuest(any()) } returns "new-q1"

            val result = service.createQuest(request, "user1", "太郎", now)

            val success = assertIs<QuestResult.Success<Quest>>(result)
            assertEquals("new-q1", success.data.id)
            assertEquals("買い物", success.data.title)
            assertEquals(QuestCategory.Errand, success.data.category)
            assertEquals(QuestStatus.Open, success.data.status)
            assertEquals("2024-07-15T10:00:00Z", success.data.createdAt)
        }

    @Test
    fun createQuestAtLimitReturnsConflict() =
        runTest {
            val request =
                CreateQuestRequest(
                    title = "買い物",
                    description = "牛乳を買ってきて",
                    category = QuestCategory.Errand,
                    rewardPoints = 20,
                )

            coEvery { questRepository.countActiveQuests() } returns 10

            val result = service.createQuest(request, "user1", "太郎")

            assertIs<QuestResult.Conflict>(result)
        }

    // --- acceptQuest ---

    @Test
    fun acceptQuestOpenReturnsSuccess() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Open",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)
            coEvery { questRepository.updateQuest("q1", any()) } just runs

            val result = service.acceptQuest("q1", "user2", "花子")

            val success = assertIs<QuestResult.Success<Quest>>(result)
            assertEquals(QuestStatus.Accepted, success.data.status)
            assertEquals("user2", success.data.assigneeUid)
            assertEquals("花子", success.data.assigneeName)
        }

    @Test
    fun acceptQuestOwnQuestReturnsForbidden() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Open",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)

            val result = service.acceptQuest("q1", "user1", "太郎")

            assertIs<QuestResult.Forbidden>(result)
        }

    @Test
    fun acceptQuestNotOpenReturnsConflict() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Accepted",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)

            val result = service.acceptQuest("q1", "user2", "花子")

            assertIs<QuestResult.Conflict>(result)
        }

    @Test
    fun acceptQuestNotFoundReturnsNotFound() =
        runTest {
            coEvery { questRepository.getQuest("nonexistent") } returns null

            val result = service.acceptQuest("nonexistent", "user2", "花子")

            assertIs<QuestResult.NotFound>(result)
        }

    // --- verifyQuest ---

    @Test
    fun verifyQuestAcceptedByCreatorReturnsSuccessAndAwardsPoints() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "assigneeUid" to "user2",
                    "assigneeName" to "花子",
                    "status" to "Accepted",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)
            coEvery { questRepository.updateQuest("q1", any()) } just runs
            coEvery { pointRepository.awardPoints(any(), any(), any(), any(), any()) } just runs

            val result = service.verifyQuest("q1", "user1")

            val success = assertIs<QuestResult.Success<Quest>>(result)
            assertEquals(QuestStatus.Verified, success.data.status)
            coVerify { pointRepository.awardPoints("user2", "花子", 50, "クエスト達成: 掃除", questId = "q1") }
        }

    @Test
    fun verifyQuestNotCreatorReturnsForbidden() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "assigneeUid" to "user2",
                    "assigneeName" to "花子",
                    "status" to "Accepted",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)

            val result = service.verifyQuest("q1", "user2")

            assertIs<QuestResult.Forbidden>(result)
        }

    @Test
    fun verifyQuestNotAcceptedReturnsConflict() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Open",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)

            val result = service.verifyQuest("q1", "user1")

            assertIs<QuestResult.Conflict>(result)
        }

    // --- deleteQuest ---

    @Test
    fun deleteQuestOpenByCreatorReturnsSuccess() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Open",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)
            coEvery { questRepository.deleteQuest("q1") } just runs

            val result = service.deleteQuest("q1", "user1")

            assertIs<QuestResult.Success<*>>(result)
            coVerify { questRepository.deleteQuest("q1") }
        }

    @Test
    fun deleteQuestExpiredByCreatorReturnsSuccess() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Expired",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)
            coEvery { questRepository.deleteQuest("q1") } just runs

            val result = service.deleteQuest("q1", "user1")

            assertIs<QuestResult.Success<*>>(result)
        }

    @Test
    fun deleteQuestAcceptedReturnsConflict() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Accepted",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)

            val result = service.deleteQuest("q1", "user1")

            assertIs<QuestResult.Conflict>(result)
        }

    @Test
    fun deleteQuestNotCreatorReturnsForbidden() =
        runTest {
            val questData =
                mapOf<String, Any>(
                    "title" to "掃除",
                    "description" to "部屋掃除",
                    "category" to "Cleaning",
                    "rewardPoints" to 50,
                    "creatorUid" to "user1",
                    "creatorName" to "太郎",
                    "status" to "Open",
                    "createdAt" to "2024-07-01T00:00:00Z",
                )

            coEvery { questRepository.getQuest("q1") } returns ("q1" to questData)

            val result = service.deleteQuest("q1", "user2")

            assertIs<QuestResult.Forbidden>(result)
        }
}
