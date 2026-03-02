package feature.settings

import core.network.CacheRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.CacheRefreshResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CacheRefreshViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cacheRepository: CacheRepository
    private lateinit var viewModel: CacheRefreshViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cacheRepository = mockk()
        viewModel = CacheRefreshViewModel(cacheRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state`() {
        assertFalse(viewModel.uiState.isClearing)
        assertNull(viewModel.uiState.message)
    }

    @Test
    fun `clear cache success shows message`() =
        runTest {
            coEvery { cacheRepository.clearServerCache() } returns
                CacheRefreshResult(
                    clearedCaches = listOf("items", "users"),
                    message = "2件のキャッシュをクリアしました",
                )

            viewModel.onClearCache()
            assertTrue(viewModel.uiState.isClearing)

            advanceUntilIdle()

            assertFalse(viewModel.uiState.isClearing)
            assertEquals("2件のキャッシュをクリアしました", viewModel.uiState.message)
        }

    @Test
    fun `clear cache failure shows error message`() =
        runTest {
            coEvery { cacheRepository.clearServerCache() } throws RuntimeException("network error")

            viewModel.onClearCache()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isClearing)
            assertEquals("キャッシュクリア失敗: network error", viewModel.uiState.message)
        }
}
