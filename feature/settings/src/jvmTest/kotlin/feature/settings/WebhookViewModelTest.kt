package feature.settings

import core.network.WebhookRepository
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
import model.WebhookSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WebhookViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var webhookRepository: WebhookRepository

    private val testSettings =
        WebhookSettings(
            url = "https://hooks.example.com/webhook",
            enabled = true,
            events = listOf("quest_created", "quest_completed"),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        webhookRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WebhookViewModel {
        coEvery { webhookRepository.getSettings() } returns testSettings
        return WebhookViewModel(webhookRepository)
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
            coEvery { webhookRepository.getSettings() } throws RuntimeException("load error")
            val viewModel = WebhookViewModel(webhookRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("読み込み失敗: load error", viewModel.uiState.message)
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

            coEvery { webhookRepository.updateSettings(any()) } returns testSettings

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存しました", viewModel.uiState.message)
            coVerify {
                webhookRepository.updateSettings(
                    WebhookSettings(
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

            coEvery { webhookRepository.updateSettings(any()) } throws RuntimeException("save error")

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存失敗: save error", viewModel.uiState.message)
        }
}
