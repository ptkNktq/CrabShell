package feature.settings

import core.network.PaymentWebhookRepository
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
import model.PaymentWebhookSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentWebhookViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var paymentWebhookRepository: PaymentWebhookRepository

    private val testSettings =
        PaymentWebhookSettings(
            url = "https://hooks.example.com/payment",
            enabled = true,
            message = "@everyone",
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        paymentWebhookRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PaymentWebhookViewModel {
        coEvery { paymentWebhookRepository.getSettings() } returns testSettings
        return PaymentWebhookViewModel(paymentWebhookRepository)
    }

    @Test
    fun `init loads settings`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("https://hooks.example.com/payment", viewModel.uiState.url)
            assertTrue(viewModel.uiState.enabled)
            assertEquals("@everyone", viewModel.uiState.message)
        }

    @Test
    fun `init load failure shows error`() =
        runTest {
            coEvery { paymentWebhookRepository.getSettings() } throws RuntimeException("load error")
            val viewModel = PaymentWebhookViewModel(paymentWebhookRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.loadError)
            assertEquals("load error", viewModel.uiState.loadErrorMessage)
        }

    @Test
    fun `loadSettings clears stale error message on retry`() =
        runTest {
            coEvery { paymentWebhookRepository.getSettings() } throws RuntimeException("first failure")
            val viewModel = PaymentWebhookViewModel(paymentWebhookRepository)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.loadError)

            coEvery { paymentWebhookRepository.getSettings() } returns testSettings
            viewModel.loadSettings()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.loadError)
            assertNull(viewModel.uiState.loadErrorMessage)
            assertEquals("https://hooks.example.com/payment", viewModel.uiState.url)
        }

    @Test
    fun `onUrlChanged updates url and clears status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onUrlChanged("https://new.example.com")

            assertEquals("https://new.example.com", viewModel.uiState.url)
            assertNull(viewModel.uiState.statusMessage)
        }

    @Test
    fun `onEnabledChanged updates enabled and clears status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEnabledChanged(false)

            assertFalse(viewModel.uiState.enabled)
            assertNull(viewModel.uiState.statusMessage)
        }

    @Test
    fun `onMessageChanged updates message and clears status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onMessageChanged("通知本文")

            assertEquals("通知本文", viewModel.uiState.message)
            assertNull(viewModel.uiState.statusMessage)
        }

    @Test
    fun `save success shows status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { paymentWebhookRepository.updateSettings(any()) } returns testSettings

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存しました", viewModel.uiState.statusMessage)
            coVerify {
                paymentWebhookRepository.updateSettings(
                    PaymentWebhookSettings(
                        url = "https://hooks.example.com/payment",
                        enabled = true,
                        message = "@everyone",
                    ),
                )
            }
        }

    @Test
    fun `save failure shows error in status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { paymentWebhookRepository.updateSettings(any()) } throws RuntimeException("save error")

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存失敗: save error", viewModel.uiState.statusMessage)
        }
}
