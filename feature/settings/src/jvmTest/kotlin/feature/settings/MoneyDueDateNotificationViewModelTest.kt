package feature.settings

import core.network.MoneyDueDateNotificationRepository
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
import model.MoneyDueDateNotificationSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyDueDateNotificationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MoneyDueDateNotificationRepository

    private val testSettings =
        MoneyDueDateNotificationSettings(
            enabled = true,
            webhookUrl = "https://hooks.example.com/webhook",
            daysBefore = 1,
            notifyHour = 23,
            prefix = "@everyone",
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MoneyDueDateNotificationViewModel {
        coEvery { repository.getSettings() } returns testSettings
        return MoneyDueDateNotificationViewModel(repository)
    }

    @Test
    fun `init loads settings`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.enabled)
            assertEquals("https://hooks.example.com/webhook", viewModel.uiState.webhookUrl)
            assertEquals("1", viewModel.uiState.daysBefore)
            assertEquals("23", viewModel.uiState.notifyHour)
            assertEquals("@everyone", viewModel.uiState.prefix)
        }

    @Test
    fun `init load failure shows error`() =
        runTest {
            coEvery { repository.getSettings() } throws RuntimeException("load error")
            val viewModel = MoneyDueDateNotificationViewModel(repository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.loadError)
            assertEquals("load error", viewModel.uiState.loadErrorMessage)
        }

    @Test
    fun `onDaysBeforeChanged strips non-digits`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onDaysBeforeChanged("2a")

            assertEquals("2", viewModel.uiState.daysBefore)
            assertNull(viewModel.uiState.statusMessage)
        }

    @Test
    fun `isDaysBeforeValid rejects negative and non-numeric values`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onDaysBeforeChanged("0")
            assertTrue(viewModel.uiState.isDaysBeforeValid)

            viewModel.onDaysBeforeChanged("")
            assertFalse(viewModel.uiState.isDaysBeforeValid)
        }

    @Test
    fun `isNotifyHourValid rejects out-of-range values`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onNotifyHourChanged("23")
            assertTrue(viewModel.uiState.isNotifyHourValid)

            viewModel.onNotifyHourChanged("24")
            assertFalse(viewModel.uiState.isNotifyHourValid)
        }

    @Test
    fun `save success shows status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { repository.updateSettings(any()) } returns testSettings

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存しました", viewModel.uiState.statusMessage)
            coVerify {
                repository.updateSettings(
                    MoneyDueDateNotificationSettings(
                        enabled = true,
                        webhookUrl = "https://hooks.example.com/webhook",
                        daysBefore = 1,
                        notifyHour = 23,
                        prefix = "@everyone",
                    ),
                )
            }
        }

    @Test
    fun `save failure shows error in status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { repository.updateSettings(any()) } throws RuntimeException("save error")

            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存に失敗しました: save error", viewModel.uiState.statusMessage)
        }

    @Test
    fun `test success shows status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { repository.test() } returns Unit

            viewModel.onTest()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isTesting)
            assertEquals("テスト送信しました", viewModel.uiState.statusMessage)
            coVerify { repository.test() }
        }

    @Test
    fun `test failure shows error in status message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { repository.test() } throws RuntimeException("test error")

            viewModel.onTest()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isTesting)
            assertEquals("テスト送信失敗: test error", viewModel.uiState.statusMessage)
        }
}
