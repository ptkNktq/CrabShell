package feature.settings

import core.network.McpConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.McpConnection
import model.McpConnectionsResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class McpConnectionsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<McpConnectionRepository>()

    private val claudeCode =
        McpConnection(id = "c1", clientName = "Claude Code", firstUsedAt = "2026-10-04T11:16:46Z", lastUsedAt = "2026-10-21T00:03:04Z")
    private val cursor =
        McpConnection(id = "c2", clientName = "Cursor", firstUsedAt = "2026-10-10T00:00:00Z", lastUsedAt = "2026-10-12T00:00:00Z")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getConnections() } returns McpConnectionsResponse(enabled = true, connections = listOf(claudeCode, cursor))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsConnectionsOnInit() =
        runTest(testDispatcher) {
            val vm = McpConnectionsViewModel(repository)
            advanceUntilIdle()

            assertFalse(vm.uiState.isLoading)
            assertTrue(vm.uiState.enabled)
            assertEquals(listOf(claudeCode, cursor), vm.uiState.connections)
        }

    @Test
    fun loadFailureShowsRetry() =
        runTest(testDispatcher) {
            coEvery { repository.getConnections() } throws Exception("network error")
            val vm = McpConnectionsViewModel(repository)
            advanceUntilIdle()

            assertTrue(vm.uiState.loadError)
            assertEquals("network error", vm.uiState.loadErrorMessage)
        }

    @Test
    fun disableRequiresConfirmationAndReplacesConnection() =
        runTest(testDispatcher) {
            val disabled = claudeCode.copy(disabledAt = "2026-10-22T00:00:00Z")
            val result = CompletableDeferred<McpConnection>()
            coEvery { repository.disable("c1") } coAnswers { result.await() }
            val vm = McpConnectionsViewModel(repository)
            advanceUntilIdle()

            vm.onRequestDisable("c1")
            assertEquals("c1", vm.uiState.confirmingDisableId)
            coVerify(exactly = 0) { repository.disable(any()) }

            vm.onConfirmDisable("c1")
            // 実行中フラグは launch の前に立つ
            assertEquals("c1", vm.uiState.updatingId)
            // 実行中は他の操作を受け付けない
            vm.onEnable("c2")
            vm.onCancelDisable()
            assertEquals("c1", vm.uiState.confirmingDisableId)

            result.complete(disabled)
            advanceUntilIdle()

            assertNull(vm.uiState.updatingId)
            assertNull(vm.uiState.confirmingDisableId)
            assertEquals(listOf(disabled, cursor), vm.uiState.connections)
            assertEquals("「Claude Code」を無効にしました", vm.uiState.successMessage)
            coVerify(exactly = 0) { repository.enable(any()) }
        }

    @Test
    fun enableReplacesConnection() =
        runTest(testDispatcher) {
            val disabledCursor = cursor.copy(disabledAt = "2026-10-15T00:00:00Z")
            coEvery { repository.getConnections() } returns
                McpConnectionsResponse(enabled = true, connections = listOf(claudeCode, disabledCursor))
            coEvery { repository.enable("c2") } returns cursor
            val vm = McpConnectionsViewModel(repository)
            advanceUntilIdle()

            vm.onEnable("c2")
            advanceUntilIdle()

            assertEquals(listOf(claudeCode, cursor), vm.uiState.connections)
            assertEquals(
                "「Cursor」を有効にしました。${McpConnectionsViewModel.ENABLED_RECONNECT_NOTE}",
                vm.uiState.successMessage,
            )
        }

    @Test
    fun updateFailureKeepsConfirmationAndShowsError() =
        runTest(testDispatcher) {
            coEvery { repository.disable("c1") } throws Exception("server error")
            val vm = McpConnectionsViewModel(repository)
            advanceUntilIdle()

            vm.onRequestDisable("c1")
            vm.onConfirmDisable("c1")
            advanceUntilIdle()

            assertNull(vm.uiState.updatingId)
            assertEquals("c1", vm.uiState.confirmingDisableId)
            assertEquals(McpConnectionsViewModel.DISABLE_FAILED_MESSAGE, vm.uiState.errorMessage)
            assertEquals(listOf(claudeCode, cursor), vm.uiState.connections)
        }
}
