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
import model.PasskeyCredentialInfo
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
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(emptyList())

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
            val credentials =
                listOf(
                    PasskeyCredentialInfo(id = 1, createdAt = "2026-01-01T00:00:00Z", transports = listOf("internal")),
                    PasskeyCredentialInfo(id = 2, createdAt = "2026-02-01T00:00:00Z"),
                )
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(credentials)

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertTrue(viewModel.uiState.isAvailable)
            assertEquals(2, viewModel.uiState.credentialCount)
            assertEquals(credentials, viewModel.uiState.credentials)
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
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(emptyList())
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
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(emptyList())

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
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(emptyList())

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            coEvery { passkeyRepository.registerPasskey() } returns
                Result.failure(RuntimeException())

            viewModel.onRegisterPasskey()
            advanceUntilIdle()

            assertEquals("パスキーの登録に失敗しました", viewModel.uiState.errorMessage)
        }

    @Test
    fun `delete passkey success shows message and reloads status and credentials`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = true, credentialCount = 1))
            coEvery { passkeyRepository.getPasskeyCredentials() } returns
                Result.success(listOf(PasskeyCredentialInfo(id = 1, createdAt = "2026-01-01T00:00:00Z")))

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            coEvery { passkeyRepository.deletePasskey(1) } returns Result.success(Unit)
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = false, credentialCount = 0))
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(emptyList())

            viewModel.onDeletePasskey(1)
            advanceUntilIdle()

            assertNull(viewModel.uiState.deletingCredentialId)
            assertEquals("パスキーを削除しました", viewModel.uiState.successMessage)
            assertNull(viewModel.uiState.errorMessage)
            assertEquals(emptyList(), viewModel.uiState.credentials)
            coVerify(exactly = 1) { passkeyRepository.deletePasskey(1) }
        }

    @Test
    fun `delete passkey failure shows error and keeps existing list`() =
        runTest {
            coEvery { passkeyRepository.getPasskeyStatus() } returns
                Result.success(PasskeyStatusResponse(registered = true, credentialCount = 1))
            val credentials = listOf(PasskeyCredentialInfo(id = 1, createdAt = "2026-01-01T00:00:00Z"))
            coEvery { passkeyRepository.getPasskeyCredentials() } returns Result.success(credentials)

            val viewModel = PasskeyManagementViewModel(passkeyRepository)
            advanceUntilIdle()

            coEvery { passkeyRepository.deletePasskey(1) } returns
                Result.failure(RuntimeException("対象のパスキーが見つかりません"))

            viewModel.onDeletePasskey(1)
            advanceUntilIdle()

            assertNull(viewModel.uiState.deletingCredentialId)
            assertEquals("対象のパスキーが見つかりません", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.successMessage)
            assertEquals(credentials, viewModel.uiState.credentials)
        }
}
