package feature.auth

import core.network.McpAuthorizationRepository
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successSetsRedirectUriAndKeepsCompleting() =
        runTest(testDispatcher) {
            coEvery { repository.complete("01J3X4Y5Z6") } returns "https://example.authkit.app/consent"
            val vm = McpConnectViewModel(repository)

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
            val vm = McpConnectViewModel(repository)

            vm.onContinue("01J3X4Y5Z6")
            advanceUntilIdle()

            assertFalse(vm.uiState.isCompleting)
            assertNull(vm.uiState.redirectUri)
            assertNotNull(vm.uiState.errorMessage)
        }

    @Test
    fun missingExternalAuthIdShowsErrorWithoutRequest() =
        runTest(testDispatcher) {
            val vm = McpConnectViewModel(repository)

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
            val vm = McpConnectViewModel(repository)

            vm.onContinue("01J3X4Y5Z6")
            vm.onContinue("01J3X4Y5Z6")
            pending.complete("https://example.authkit.app/consent")
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.complete(any()) }
        }

    @Test
    fun cancelEndsConnectionWithoutRequest() =
        runTest(testDispatcher) {
            val vm = McpConnectViewModel(repository)

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
            val vm = McpConnectViewModel(repository)

            vm.onContinue("01J3X4Y5Z6")
            vm.onCancel()
            pending.complete("https://example.authkit.app/consent")
            advanceUntilIdle()

            assertFalse(vm.uiState.isCancelled)
            assertEquals("https://example.authkit.app/consent", vm.uiState.redirectUri)
        }
}
