package feature.settings

import core.network.QuestWebhookRepository
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
import model.QuestWebhookSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuestWebhookViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var questWebhookRepository: QuestWebhookRepository

    private val testSettings =
        QuestWebhookSettings(
            url = "https://hooks.example.com/webhook",
            enabled = true,
            events = listOf("quest_created", "quest_completed"),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        questWebhookRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): QuestWebhookViewModel {
        coEvery { questWebhookRepository.getSettings() } returns testSettings
        return QuestWebhookViewModel(questWebhookRepository)
    }

    @Test
    fun `init loads settings`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("https://hooks.example.com/webhook", viewModel.uiState.url)
            assertTrue(viewModel.uiState.enabled)
            assertEquals(listOf("quest_created", "quest_completed"), viewModel.uiState.events)
        }

    @Test
    fun `init load failure shows error`() =
        runTest {
            coEvery { questWebhookRepository.getSettings() } throws RuntimeException("load error")
            val viewModel = QuestWebhookViewModel(questWebhookRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.loadError)
        }

    @Test
    fun `onUrlChanged updates url and clears message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onUrlChanged("https://new-url.example.com")

            assertEquals("https://new-url.example.com", viewModel.uiState.url)
            assertNull(viewModel.uiState.message)
        }

    @Test
    fun `onEnabledChanged updates enabled and clears message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEnabledChanged(false)

            assertFalse(viewModel.uiState.enabled)
            assertNull(viewModel.uiState.message)
        }

    @Test
    fun `onToggleEvent adds event`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onToggleEvent("quest_verified")

            assertEquals(
                listOf("quest_created", "quest_completed", "quest_verified"),
                viewModel.uiState.events,
            )
            assertNull(viewModel.uiState.message)
        }

    @Test
    fun `onToggleEvent removes event`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onToggleEvent("quest_created")

            assertEquals(listOf("quest_completed"), viewModel.uiState.events)
        }

    @Test
    fun `save success shows message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { questWebhookRepository.updateSettings(any()) } returns testSettings

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存しました", viewModel.uiState.message)
            coVerify {
                questWebhookRepository.updateSettings(
                    QuestWebhookSettings(
                        url = "https://hooks.example.com/webhook",
                        enabled = true,
                        events = listOf("quest_created", "quest_completed"),
                    ),
                )
            }
        }

    @Test
    fun `save failure shows error`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { questWebhookRepository.updateSettings(any()) } throws RuntimeException("save error")

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存失敗: save error", viewModel.uiState.message)
        }
}
