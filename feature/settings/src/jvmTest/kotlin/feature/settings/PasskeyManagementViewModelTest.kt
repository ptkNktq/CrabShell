package feature.settings

import core.network.PasskeyRepository
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
import model.PasskeyStatusResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyManagementViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var passkeyRepository: PasskeyRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        passkeyRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads status - available`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = false, credentialCount = 0))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.isAvailable)
            assertEquals(0, viewModel.uiState.credentialCount)
        }

    @Test
    fun `init loads status - registered with credentials`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = true, credentialCount = 2))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.isAvailable)
            assertEquals(2, viewModel.uiState.credentialCount)
        }

    @Test
    fun `init loads status - registered but zero credentials means unavailable`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = true, credentialCount = 0))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertFalse(viewModel.uiState.isAvailable)
        }

    @Test
    fun `init load failure sets unavailable`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.failure(RuntimeException("error"))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertFalse(viewModel.uiState.isAvailable)
        }

    @Test
    fun `register passkey success shows message and reloads status`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = false, credentialCount = 0))
            coEvery { passkeyRepository.registerPasskey() } returns Result.success(Unit)

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            // After registration, status will show 1 credential
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = true, credentialCount = 1))

            viewModel.onRegisterPasskey()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isRegistering)
            assertEquals("パスキーを登録しました", viewModel.uiState.successMessage)
            assertNull(viewModel.uiState.errorMessage)
            assertEquals(1, viewModel.uiState.credentialCount)
            // Verify status was reloaded (called twice: init + after register)
            coVerify(exactly = 2) { passkeyRepository.getPasskeyStatus() }
        }

    @Test
    fun `register passkey failure shows error`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = false, credentialCount = 0))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            coEvery { passkeyRepository.registerPasskey() } returns
                Result.failure(RuntimeException("registration failed"))

            viewModel.onRegisterPasskey()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isRegistering)
            assertEquals("registration failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.successMessage)
        }

    @Test
    fun `register passkey failure with no message shows default error`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = false, credentialCount = 0))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            coEvery { passkeyRepository.registerPasskey() } returns
                Result.failure(RuntimeException())

            viewModel.onRegisterPasskey()
            advanceUntilIdle()

            assertEquals("パスキーの登録に失敗しました", viewModel.uiState.errorMessage)
        }
}
