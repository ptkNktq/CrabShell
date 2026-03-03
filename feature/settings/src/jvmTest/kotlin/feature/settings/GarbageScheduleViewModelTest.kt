package feature.settings

import core.network.GarbageScheduleRepository
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
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GarbageScheduleViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var garbageScheduleRepository: GarbageScheduleRepository

    private val testSchedules =
        listOf(
            GarbageTypeSchedule(
                garbageType = GarbageType.BURNABLE,
                daysOfWeek = listOf(1, 4),
                frequency = CollectionFrequency.WEEKLY,
            ),
            GarbageTypeSchedule(
                garbageType = GarbageType.RECYCLABLE,
                daysOfWeek = listOf(3),
                frequency = CollectionFrequency.WEEK_1_3,
            ),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        garbageScheduleRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GarbageScheduleViewModel {
        coEvery { garbageScheduleRepository.getSchedules() } returns testSchedules
        return GarbageScheduleViewModel(garbageScheduleRepository)
    }

    @Test
    fun `init loads schedules with all GarbageType entries`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val schedules = viewModel.uiState.schedules
            assertFalse(viewModel.uiState.isLoading)
            assertEquals(GarbageType.entries.size, schedules.size)

            val burnable = schedules.first { it.garbageType == GarbageType.BURNABLE }
            assertEquals(listOf(1, 4), burnable.daysOfWeek)
            assertEquals(CollectionFrequency.WEEKLY, burnable.frequency)

            val nonBurnable = schedules.first { it.garbageType == GarbageType.NON_BURNABLE }
            assertEquals(emptyList(), nonBurnable.daysOfWeek)

            val recyclable = schedules.first { it.garbageType == GarbageType.RECYCLABLE }
            assertEquals(listOf(3), recyclable.daysOfWeek)
            assertEquals(CollectionFrequency.WEEK_1_3, recyclable.frequency)
        }

    @Test
    fun `init load failure keeps default schedules`() =
        runTest {
            coEvery { garbageScheduleRepository.getSchedules() } throws RuntimeException("error")
            val viewModel = GarbageScheduleViewModel(garbageScheduleRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals(GarbageType.entries.size, viewModel.uiState.schedules.size)
            viewModel.uiState.schedules.forEach {
                assertEquals(emptyList(), it.daysOfWeek)
            }
        }

    @Test
    fun `toggle day adds day`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onToggleDay(GarbageType.BURNABLE, 3)

            val burnable = viewModel.uiState.schedules.first { it.garbageType == GarbageType.BURNABLE }
            assertEquals(listOf(1, 3, 4), burnable.daysOfWeek)
        }

    @Test
    fun `toggle day removes day`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onToggleDay(GarbageType.BURNABLE, 1)

            val burnable = viewModel.uiState.schedules.first { it.garbageType == GarbageType.BURNABLE }
            assertEquals(listOf(4), burnable.daysOfWeek)
        }

    @Test
    fun `toggle day clears message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { garbageScheduleRepository.saveSchedules(any()) } returns Unit
            viewModel.onSaveSchedule()
            advanceUntilIdle()
            assertEquals("保存しました", viewModel.uiState.message)

            viewModel.onToggleDay(GarbageType.BURNABLE, 2)
            assertEquals(null, viewModel.uiState.message)
        }

    @Test
    fun `change frequency updates schedule`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onChangeFrequency(GarbageType.BURNABLE, CollectionFrequency.WEEK_2_4)

            val burnable = viewModel.uiState.schedules.first { it.garbageType == GarbageType.BURNABLE }
            assertEquals(CollectionFrequency.WEEK_2_4, burnable.frequency)
        }

    @Test
    fun `save success shows message`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { garbageScheduleRepository.saveSchedules(any()) } returns Unit

            viewModel.onSaveSchedule()
            assertTrue(viewModel.uiState.isSaving)

            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存しました", viewModel.uiState.message)
            coVerify { garbageScheduleRepository.saveSchedules(viewModel.uiState.schedules) }
        }

    @Test
    fun `save failure shows error`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { garbageScheduleRepository.saveSchedules(any()) } throws RuntimeException("save error")

            viewModel.onSaveSchedule()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存に失敗しました: save error", viewModel.uiState.message)
        }
}
