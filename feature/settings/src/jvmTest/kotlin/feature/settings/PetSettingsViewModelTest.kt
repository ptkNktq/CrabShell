package feature.settings

import core.common.FeedingSettingsChangedEvent
import core.network.FeedingSettingsRepository
import core.network.PetRepository
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
import model.FeedingSettings
import model.MealTime
import model.Pet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PetSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var petRepository: PetRepository
    private lateinit var feedingSettingsRepository: FeedingSettingsRepository
    private lateinit var changedEvent: FeedingSettingsChangedEvent

    private val testPets = listOf(Pet(id = "p1", name = "ぽち"))

    private val testSettings =
        FeedingSettings(
            mealOrder = listOf(MealTime.MORNING, MealTime.LUNCH, MealTime.EVENING),
            mealTimes =
                mapOf(
                    MealTime.MORNING to "07:00",
                    MealTime.LUNCH to "12:00",
                    MealTime.EVENING to "18:00",
                ),
            reminderEnabled = true,
            reminderWebhookUrl = "https://server.example.com/webhook",
            reminderDelayMinutes = 30,
            reminderPrefix = "@here",
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        petRepository = mockk()
        feedingSettingsRepository = mockk()
        changedEvent = FeedingSettingsChangedEvent()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PetSettingsViewModel {
        coEvery { petRepository.getPets() } returns testPets
        coEvery { feedingSettingsRepository.getSettings() } returns testSettings
        return PetSettingsViewModel(petRepository, feedingSettingsRepository, changedEvent)
    }

    @Test
    fun `init loads pets and feeding settings`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals(testPets, viewModel.uiState.pets)
            assertEquals(testSettings.mealOrder, viewModel.uiState.mealOrder)
            assertEquals("https://server.example.com/webhook", viewModel.uiState.reminderWebhookUrl)
        }

    @Test
    fun `onSaveFeeding persists meal fields without clobbering in-progress reminder edits`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // リマインダーを編集（未保存）した状態でごはん設定を保存する
            viewModel.onReminderWebhookUrlChanged("https://EDITED.example.com")
            viewModel.onMealTimeChanged(MealTime.MORNING, "08:00")

            coEvery { feedingSettingsRepository.updateSettings(any()) } returns testSettings
            viewModel.onSaveFeeding()
            advanceUntilIdle()

            // PUT にはごはんの編集値が入り、リマインダーは保存直前に取得したサーバー値が保たれる
            coVerify {
                feedingSettingsRepository.updateSettings(
                    match {
                        it.mealTimes[MealTime.MORNING] == "08:00" &&
                            it.reminderWebhookUrl == "https://server.example.com/webhook"
                    },
                )
            }
        }

    @Test
    fun `onSaveReminder persists reminder fields without clobbering in-progress meal edits`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // ごはん設定を編集（未保存）した状態でリマインダーを保存する
            viewModel.onMealTimeChanged(MealTime.MORNING, "09:00")
            viewModel.onReminderWebhookUrlChanged("https://new-reminder.example.com")

            coEvery { feedingSettingsRepository.updateSettings(any()) } returns testSettings
            viewModel.onSaveReminder()
            advanceUntilIdle()

            // PUT にはリマインダーの編集値が入り、ごはんの時刻は保存直前に取得したサーバー値が保たれる
            coVerify {
                feedingSettingsRepository.updateSettings(
                    match {
                        it.reminderWebhookUrl == "https://new-reminder.example.com" &&
                            it.mealTimes[MealTime.MORNING] == "07:00"
                    },
                )
            }
        }

    @Test
    fun `onSaveFeeding success sets only feeding message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { feedingSettingsRepository.updateSettings(any()) } returns testSettings
            viewModel.onSaveFeeding()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.feedingSaving)
            assertEquals("設定を保存しました", viewModel.uiState.feedingMessage)
            assertNull(viewModel.uiState.reminderMessage)
            assertNull(viewModel.uiState.petNameMessage)
        }

    @Test
    fun `onSaveReminder success sets only reminder message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { feedingSettingsRepository.updateSettings(any()) } returns testSettings
            viewModel.onSaveReminder()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.reminderSaving)
            assertEquals("設定を保存しました", viewModel.uiState.reminderMessage)
            assertNull(viewModel.uiState.feedingMessage)
            assertNull(viewModel.uiState.petNameMessage)
        }

    @Test
    fun `onSaveFeeding failure sets feeding error message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { feedingSettingsRepository.updateSettings(any()) } throws RuntimeException("save error")
            viewModel.onSaveFeeding()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.feedingSaving)
            assertEquals("保存失敗: save error", viewModel.uiState.feedingMessage)
            assertNull(viewModel.uiState.reminderMessage)
        }

    @Test
    fun `onSavePetName success sets only pet name message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onPetNameChanged("p1", "たま")
            coEvery { petRepository.updatePetName("p1", "たま") } returns Pet(id = "p1", name = "たま")
            viewModel.onSavePetName("p1")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.petNameSaving)
            assertEquals("ペット名を更新しました", viewModel.uiState.petNameMessage)
            assertEquals(
                "たま",
                viewModel.uiState.pets
                    .first { it.id == "p1" }
                    .name,
            )
            assertNull(viewModel.uiState.feedingMessage)
            assertNull(viewModel.uiState.reminderMessage)
        }

    @Test
    fun `onTestReminder success sets reminder message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { feedingSettingsRepository.testReminder() } returns Unit
            viewModel.onTestReminder()
            advanceUntilIdle()

            assertNull(viewModel.uiState.testingPhase)
            assertEquals("リマインダーをテスト送信しました", viewModel.uiState.reminderMessage)
            assertNull(viewModel.uiState.feedingMessage)
        }
}
