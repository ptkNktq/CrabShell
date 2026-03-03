package feature.quest

import core.network.PointRepository
import core.network.QuestRepository
import core.network.RewardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.CreateQuestRequest
import model.CreateRewardRequest
import model.GenerateQuestTextResponse
import model.PointHistory
import model.Quest
import model.QuestCategory
import model.QuestStatus
import model.Reward
import model.UserPoints
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuestViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var questRepository: QuestRepository
    private lateinit var pointRepository: PointRepository
    private lateinit var rewardRepository: RewardRepository

    private val testQuests =
        listOf(
            Quest(id = "q1", title = "掃除", status = QuestStatus.Open, rewardPoints = 10),
            Quest(id = "q2", title = "料理", status = QuestStatus.Accepted, rewardPoints = 20),
        )

    private val testPoints = UserPoints(uid = "u1", displayName = "Alice", balance = 100)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        questRepository = mockk()
        pointRepository = mockk()
        rewardRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): QuestViewModel {
        coEvery { questRepository.getQuests(null) } returns testQuests
        coEvery { pointRepository.getMyPoints() } returns testPoints
        coEvery { questRepository.isAiAvailable() } returns true
        return QuestViewModel(questRepository, pointRepository, rewardRepository)
    }

    // --- init ---

    @Test
    fun `init loads quests, points, and ai availability`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals(testQuests, viewModel.uiState.quests)
            assertEquals(testPoints, viewModel.uiState.myPoints)
            assertTrue(viewModel.uiState.isAiAvailable)
        }

    @Test
    fun `init filters out Verified quests`() =
        runTest {
            val questsWithVerified =
                testQuests + Quest(id = "q3", title = "完了済み", status = QuestStatus.Verified)
            coEvery { questRepository.getQuests(null) } returns questsWithVerified
            coEvery { pointRepository.getMyPoints() } returns testPoints
            coEvery { questRepository.isAiAvailable() } returns false

            val viewModel = QuestViewModel(questRepository, pointRepository, rewardRepository)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.quests.size)
            assertTrue(viewModel.uiState.quests.none { it.status == QuestStatus.Verified })
        }

    // --- canCreateQuest ---

    @Test
    fun `canCreateQuest is true when less than 10 active quests`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.canCreateQuest)
        }

    @Test
    fun `canCreateQuest is false when 10 or more active quests`() =
        runTest {
            val manyQuests =
                (1..10).map {
                    Quest(id = "q$it", title = "Quest $it", status = QuestStatus.Open)
                }
            coEvery { questRepository.getQuests(null) } returns manyQuests
            coEvery { pointRepository.getMyPoints() } returns testPoints
            coEvery { questRepository.isAiAvailable() } returns false

            val viewModel = QuestViewModel(questRepository, pointRepository, rewardRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.canCreateQuest)
        }

    @Test
    fun `canCreateQuest only counts Open and Accepted`() =
        runTest {
            val mixedQuests =
                (1..9).map { Quest(id = "q$it", title = "Quest $it", status = QuestStatus.Open) } +
                    Quest(id = "q10", title = "Completed", status = QuestStatus.Completed)
            coEvery { questRepository.getQuests(null) } returns mixedQuests
            coEvery { pointRepository.getMyPoints() } returns testPoints
            coEvery { questRepository.isAiAvailable() } returns false

            val viewModel = QuestViewModel(questRepository, pointRepository, rewardRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.canCreateQuest)
        }

    // --- create quest ---

    @Test
    fun `create quest success adds to list`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val newQuest =
                Quest(id = "q3", title = "新クエスト", status = QuestStatus.Open, rewardPoints = 15)
            coEvery {
                questRepository.createQuest(
                    CreateQuestRequest(
                        title = "新クエスト",
                        description = "説明",
                        category = QuestCategory.Housework,
                        rewardPoints = 15,
                        deadline = null,
                    ),
                )
            } returns newQuest

            viewModel.onToggleCreateForm()
            assertTrue(viewModel.uiState.isCreating)

            viewModel.onCreateQuest("新クエスト", "説明", QuestCategory.Housework, 15, null)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isCreating)
            assertEquals(
                "q3",
                viewModel.uiState.quests
                    .first()
                    .id,
            )
            assertEquals(3, viewModel.uiState.quests.size)
        }

    @Test
    fun `create quest failure sets error`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { questRepository.createQuest(any()) } throws RuntimeException("create error")

            viewModel.onCreateQuest("クエスト", "説明", QuestCategory.Other, 5, null)
            advanceUntilIdle()

            assertEquals("create error", viewModel.uiState.error)
        }

    // --- accept quest ---

    @Test
    fun `accept quest updates quest in list`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val accepted = testQuests[0].copy(status = QuestStatus.Accepted, assigneeUid = "u1")
            coEvery { questRepository.acceptQuest("q1") } returns accepted

            viewModel.onAcceptQuest("q1")
            advanceUntilIdle()

            val updated = viewModel.uiState.quests.first { it.id == "q1" }
            assertEquals(QuestStatus.Accepted, updated.status)
            assertEquals("u1", updated.assigneeUid)
        }

    // --- verify quest ---

    @Test
    fun `verify quest removes from list and reloads points`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { questRepository.verifyQuest("q1") } returns testQuests[0].copy(status = QuestStatus.Verified)
            val updatedPoints = testPoints.copy(balance = 110)
            coEvery { pointRepository.getMyPoints() } returns updatedPoints

            viewModel.onVerifyQuest("q1")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.quests.any { it.id == "q1" })
            assertEquals(110, viewModel.uiState.myPoints?.balance)
        }

    // --- delete quest ---

    @Test
    fun `delete quest removes from list`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { questRepository.deleteQuest("q1") } returns Unit

            viewModel.onDeleteQuest("q1")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.quests.size)
            assertFalse(viewModel.uiState.quests.any { it.id == "q1" })
        }

    // --- tab switching ---

    @Test
    fun `select Rewards tab loads rewards`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val rewards = listOf(Reward(id = "r1", name = "ごほうび", cost = 50))
            coEvery { rewardRepository.getRewards() } returns rewards

            viewModel.onSelectTab(QuestTab.Rewards)
            advanceUntilIdle()

            assertEquals(QuestTab.Rewards, viewModel.uiState.currentTab)
            assertEquals(rewards, viewModel.uiState.rewards)
        }

    @Test
    fun `select History tab loads history`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val history = listOf(PointHistory(id = "h1", uid = "u1", amount = 10, reason = "クエスト完了"))
            coEvery { pointRepository.getHistory() } returns history

            viewModel.onSelectTab(QuestTab.History)
            advanceUntilIdle()

            assertEquals(QuestTab.History, viewModel.uiState.currentTab)
            assertEquals(history, viewModel.uiState.history)
        }

    @Test
    fun `select Board tab reloads quests`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSelectTab(QuestTab.Board)
            advanceUntilIdle()

            assertEquals(QuestTab.Board, viewModel.uiState.currentTab)
            coVerify(atLeast = 2) { questRepository.getQuests(null) }
        }

    // --- rewards ---

    @Test
    fun `create reward success adds to list`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val reward = Reward(id = "r1", name = "アイス", description = "好きなアイス", cost = 30)
            coEvery {
                rewardRepository.createReward(CreateRewardRequest("アイス", "好きなアイス", 30))
            } returns reward

            viewModel.onToggleCreateReward()
            assertTrue(viewModel.uiState.isCreatingReward)

            viewModel.onCreateReward("アイス", "好きなアイス", 30)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isCreatingReward)
            assertEquals(1, viewModel.uiState.rewards.size)
            assertEquals(
                "アイス",
                viewModel.uiState.rewards
                    .first()
                    .name,
            )
        }

    @Test
    fun `delete reward removes from list`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Set up rewards via tab switch
            val rewards =
                listOf(
                    Reward(id = "r1", name = "A", cost = 10),
                    Reward(id = "r2", name = "B", cost = 20),
                )
            coEvery { rewardRepository.getRewards() } returns rewards
            viewModel.onSelectTab(QuestTab.Rewards)
            advanceUntilIdle()

            coEvery { rewardRepository.deleteReward("r1") } returns Unit

            viewModel.onDeleteReward("r1")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.rewards.size)
            assertEquals(
                "r2",
                viewModel.uiState.rewards
                    .first()
                    .id,
            )
        }

    @Test
    fun `exchange reward reloads points and rewards`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { rewardRepository.exchangeReward("r1") } returns Unit
            val updatedPoints = testPoints.copy(balance = 50)
            coEvery { pointRepository.getMyPoints() } returns updatedPoints
            val updatedRewards = listOf(Reward(id = "r1", name = "A", cost = 10))
            coEvery { rewardRepository.getRewards() } returns updatedRewards

            viewModel.onExchangeReward("r1")
            advanceUntilIdle()

            assertEquals(50, viewModel.uiState.myPoints?.balance)
            coVerify { rewardRepository.exchangeReward("r1") }
        }

    // --- AI generation ---

    @Test
    fun `generate text success calls onResult`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery {
                questRepository.generateQuestText("タイトル", "説明", QuestCategory.Cooking, 10, null)
            } returns GenerateQuestTextResponse("AIタイトル", "AI説明")

            var resultTitle = ""
            var resultDesc = ""

            viewModel.onGenerateText(
                title = "タイトル",
                description = "説明",
                category = QuestCategory.Cooking,
                rewardPoints = 10,
                deadline = null,
                onResult = { t, d ->
                    resultTitle = t
                    resultDesc = d
                },
                onError = {},
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isGenerating)
            assertEquals("AIタイトル", resultTitle)
            assertEquals("AI説明", resultDesc)
        }

    @Test
    fun `generate text failure calls onError`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery {
                questRepository.generateQuestText(any(), any(), any(), any(), any())
            } throws RuntimeException("api error")

            var errorMsg = ""

            viewModel.onGenerateText(
                title = "タイトル",
                description = "",
                category = QuestCategory.Other,
                rewardPoints = 5,
                deadline = null,
                onResult = { _, _ -> },
                onError = { errorMsg = it },
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isGenerating)
            assertEquals("AI 生成に失敗しました: api error", errorMsg)
        }

    // --- error dismissal ---

    @Test
    fun `dismiss error clears error`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { questRepository.createQuest(any()) } throws RuntimeException("err")
            viewModel.onCreateQuest("x", "x", QuestCategory.Other, 1, null)
            advanceUntilIdle()
            assertEquals("err", viewModel.uiState.error)

            viewModel.onDismissError()
            assertNull(viewModel.uiState.error)
        }
}
