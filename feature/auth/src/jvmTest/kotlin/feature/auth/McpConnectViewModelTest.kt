package feature.auth

import core.network.ApiResponseException
import core.network.McpAuthorizationRepository
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
import model.McpAuthorizationErrors
import model.McpConnection
import model.McpConnectionsResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class McpConnectViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<McpAuthorizationRepository>()
    private val connectionRepository = mockk<McpConnectionRepository>()

    private fun connection(
        id: String,
        disabledAt: String? = null,
    ) = McpConnection(
        id = id,
        clientName = "app-$id",
        firstUsedAt = "2026-09-01T00:00:00Z",
        lastUsedAt = "2026-10-01T00:00:00Z",
        disabledAt = disabledAt,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { connectionRepository.getConnections() } returns McpConnectionsResponse(enabled = true, connections = emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successSetsRedirectUriAndKeepsCompleting() =
        runTest(testDispatcher) {
            coEvery { repository.complete("01J3X4Y5Z6") } returns "https://example.authkit.app/consent"
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onContinue("01J3X4Y5Z6")
            assertTrue(vm.uiState.isCompleting)
            advanceUntilIdle()

            assertEquals("https://example.authkit.app/consent", vm.uiState.redirectUri)
            // 遷移するまでボタンを押せないままにする
            assertTrue(vm.uiState.isCompleting)
        }

    @Test
    fun failureShowsErrorAndAllowsRetry() =
        runTest(testDispatcher) {
            coEvery { repository.complete(any()) } throws Exception("Failed to complete authorization")
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()

            assertFalse(vm.uiState.isCompleting)
            assertNull(vm.uiState.redirectUri)
            assertNotNull(vm.uiState.errorMessage)
        }

    @Test
    fun failureMessageDependsOnCause() =
        runTest(testDispatcher) {
            val cases =
                listOf(
                    ApiResponseException(400, McpAuthorizationErrors.EMAIL_NOT_REGISTERED) to McpConnectViewModel.MISSING_EMAIL_MESSAGE,
                    ApiResponseException(400, McpAuthorizationErrors.INVALID_EXTERNAL_AUTH_ID) to McpConnectViewModel.INVALID_LINK_MESSAGE,
                    ApiResponseException(429, "Too many requests") to McpConnectViewModel.TOO_MANY_REQUESTS_MESSAGE,
                    // サーバーの文言（内部情報を含みうる）はそのまま出さない
                    ApiResponseException(502, McpAuthorizationErrors.UPSTREAM_FAILURE) to McpConnectViewModel.FAILED_MESSAGE,
                    Exception("connection reset") to McpConnectViewModel.FAILED_MESSAGE,
                )
            cases.forEach { (error, expected) ->
                coEvery { repository.complete(any()) } throws error
                val vm = McpConnectViewModel(repository, connectionRepository)
                advanceUntilIdle()

                vm.onContinue("01J3X4Y5Z6")
                advanceUntilIdle()

                assertEquals(expected, vm.uiState.errorMessage)
            }
        }

    @Test
    fun missingExternalAuthIdShowsErrorWithoutRequest() =
        runTest(testDispatcher) {
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onContinue(null)
            vm.onContinue(" ")
            advanceUntilIdle()

            assertEquals(McpConnectViewModel.INVALID_LINK_MESSAGE, vm.uiState.errorMessage)
            coVerify(exactly = 0) { repository.complete(any()) }
        }

    @Test
    fun secondClickWhileCompletingIsIgnored() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<String>()
            coEvery { repository.complete(any()) } coAnswers { pending.await() }
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onContinue("01J3X4Y5Z6")
            vm.onContinue("01J3X4Y5Z6")
            pending.complete("https://example.authkit.app/consent")
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.complete(any()) }
        }

    @Test
    fun continueIsIgnoredUntilDisabledConnectionsAreLoaded() =
        runTest(testDispatcher) {
            val connections = CompletableDeferred<McpConnectionsResponse>()
            coEvery { connectionRepository.getConnections() } coAnswers { connections.await() }
            coEvery { repository.complete("01J3X4Y5Z6") } returns "https://example.authkit.app/consent"
            val vm = McpConnectViewModel(repository, connectionRepository)

            // 案内が出る前に続けると、無効にしたアプリだと気付かずに連携してしまう
            assertTrue(vm.uiState.isLoadingDisabledConnections)
            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()
            assertFalse(vm.uiState.isCompleting)
            coVerify(exactly = 0) { repository.complete(any()) }

            connections.complete(McpConnectionsResponse(enabled = true, connections = emptyList()))
            advanceUntilIdle()
            assertFalse(vm.uiState.isLoadingDisabledConnections)

            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()
            assertEquals("https://example.authkit.app/consent", vm.uiState.redirectUri)
        }

    @Test
    fun cancelEndsConnectionWithoutRequest() =
        runTest(testDispatcher) {
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onCancel()
            // キャンセル後は連携を続けられない
            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()

            assertTrue(vm.uiState.isCancelled)
            assertFalse(vm.uiState.isCompleting)
            coVerify(exactly = 0) { repository.complete(any()) }
        }

    @Test
    fun cancelWhileCompletingIsIgnored() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<String>()
            coEvery { repository.complete(any()) } coAnswers { pending.await() }
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onContinue("01J3X4Y5Z6")
            vm.onCancel()
            pending.complete("https://example.authkit.app/consent")
            advanceUntilIdle()

            assertFalse(vm.uiState.isCancelled)
            assertEquals("https://example.authkit.app/consent", vm.uiState.redirectUri)
        }

    @Test
    fun loadsOnlyDisabledConnectionsNewestFirst() =
        runTest(testDispatcher) {
            coEvery { connectionRepository.getConnections() } returns
                McpConnectionsResponse(
                    enabled = true,
                    connections =
                        listOf(
                            connection("active"),
                            connection("old", disabledAt = "2026-10-01T00:00:00Z"),
                            connection("new", disabledAt = "2026-10-20T00:00:00Z"),
                        ),
                )
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            assertEquals(
                listOf("new", "old"),
                vm.uiState.disabledConnections.connections
                    .map { it.id },
            )
        }

    @Test
    fun disabledConnectionsAreSortedByTimeNotByString() =
        runTest(testDispatcher) {
            coEvery { connectionRepository.getConnections() } returns
                McpConnectionsResponse(
                    enabled = true,
                    connections =
                        listOf(
                            connection("whole-second", disabledAt = "2026-10-20T00:00:05Z"),
                            connection("later-in-second", disabledAt = "2026-10-20T00:00:05.123Z"),
                        ),
                )
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            assertEquals(
                listOf("later-in-second", "whole-second"),
                vm.uiState.disabledConnections.connections
                    .map { it.id },
            )
        }

    @Test
    fun connectionsLoadFailureDoesNotBlockConnecting() =
        runTest(testDispatcher) {
            coEvery { connectionRepository.getConnections() } throws Exception("network error")
            coEvery { repository.complete("01J3X4Y5Z6") } returns "https://example.authkit.app/consent"
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            assertTrue(
                vm.uiState.disabledConnections.connections
                    .isEmpty(),
            )
            assertNull(vm.uiState.errorMessage)

            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()
            assertEquals("https://example.authkit.app/consent", vm.uiState.redirectUri)
        }

    @Test
    fun enableConnectionMarksItEnabledAndBlocksOtherRows() =
        runTest(testDispatcher) {
            coEvery { connectionRepository.getConnections() } returns
                McpConnectionsResponse(
                    enabled = true,
                    connections = listOf(connection("a", "2026-10-20T00:00:00Z"), connection("b", "2026-10-19T00:00:00Z")),
                )
            val pending = CompletableDeferred<McpConnection>()
            coEvery { connectionRepository.enable("a") } coAnswers { pending.await() }
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onEnableConnection("a")
            // 実行中フラグは launch の前に立ち、他の行は押せない
            assertEquals("a", vm.uiState.disabledConnections.enablingId)
            vm.onEnableConnection("b")

            pending.complete(connection("a"))
            advanceUntilIdle()

            assertNull(vm.uiState.disabledConnections.enablingId)
            assertEquals(setOf("a"), vm.uiState.disabledConnections.enabledIds)
            coVerify(exactly = 0) { connectionRepository.enable("b") }

            // 有効にした行はもう押せない
            vm.onEnableConnection("a")
            advanceUntilIdle()
            coVerify(exactly = 1) { connectionRepository.enable("a") }
        }

    @Test
    fun continueAndCancelAreIgnoredWhileEnabling() =
        runTest(testDispatcher) {
            coEvery { connectionRepository.getConnections() } returns
                McpConnectionsResponse(enabled = true, connections = listOf(connection("a", "2026-10-20T00:00:00Z")))
            val pending = CompletableDeferred<McpConnection>()
            coEvery { connectionRepository.enable("a") } coAnswers { pending.await() }
            coEvery { repository.complete("01J3X4Y5Z6") } returns "https://example.authkit.app/consent"
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onEnableConnection("a")
            // 有効化より先に連携が完了すると、無効のまま接続されうる
            vm.onContinue("01J3X4Y5Z6")
            vm.onCancel()
            assertFalse(vm.uiState.isCompleting)
            assertFalse(vm.uiState.isCancelled)

            pending.complete(connection("a"))
            advanceUntilIdle()
            coVerify(exactly = 0) { repository.complete(any()) }

            // 有効化が終われば続けられる
            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()
            assertEquals("https://example.authkit.app/consent", vm.uiState.redirectUri)
        }

    @Test
    fun enableConnectionFailureShowsError() =
        runTest(testDispatcher) {
            coEvery { connectionRepository.getConnections() } returns
                McpConnectionsResponse(enabled = true, connections = listOf(connection("a", "2026-10-20T00:00:00Z")))
            coEvery { connectionRepository.enable("a") } throws Exception("server error")
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onEnableConnection("a")
            advanceUntilIdle()

            assertEquals(McpConnectViewModel.ENABLE_FAILED_MESSAGE, vm.uiState.disabledConnections.errorMessage)
            assertTrue(
                vm.uiState.disabledConnections.enabledIds
                    .isEmpty(),
            )
            assertNull(vm.uiState.disabledConnections.enablingId)
        }

    @Test
    fun collapsingClearsFilter() =
        runTest(testDispatcher) {
            val vm = McpConnectViewModel(repository, connectionRepository)
            advanceUntilIdle()

            vm.onToggleDisabledExpanded()
            vm.onDisabledFilterQueryChange("claude")
            assertTrue(vm.uiState.disabledConnections.isExpanded)
            assertEquals("claude", vm.uiState.disabledConnections.filterQuery)

            vm.onToggleDisabledExpanded()
            assertFalse(vm.uiState.disabledConnections.isExpanded)
            assertEquals("", vm.uiState.disabledConnections.filterQuery)
        }
}
