package feature.auth

import core.auth.AuthRepository
import core.auth.AuthStateHolder
import core.network.PasskeyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var passkeyRepository: PasskeyRepository
    private lateinit var authStateHolder: AuthStateHolder
    private lateinit var signInWithHistoryService: SignInWithHistoryService

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk()
        passkeyRepository = mockk()
        authStateHolder = AuthStateHolder()
        signInWithHistoryService = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(webAuthnSupported: Boolean = true): LoginViewModel {
        every { authRepository.isWebAuthnSupported() } returns webAuthnSupported
        return LoginViewModel(authRepository, passkeyRepository, authStateHolder, signInWithHistoryService)
    }

    @Test
    fun `empty email and password shows error`() {
        val viewModel = createViewModel()
        viewModel.onSignIn()

        assertEquals("メールアドレスとパスワードを入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `empty email shows error`() {
        val viewModel = createViewModel()
        viewModel.onPasswordChanged("password")
        viewModel.onSignIn()

        assertEquals("メールアドレスとパスワードを入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `empty password shows error`() {
        val viewModel = createViewModel()
        viewModel.onEmailChanged("test@example.com")
        viewModel.onSignIn()

        assertEquals("メールアドレスとパスワードを入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `successful sign in keeps isLoading until auth state switches`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { signInWithHistoryService.signInWithEmail("test@example.com", "password") } returns Result.success(Unit)

            viewModel.onEmailChanged("test@example.com")
            viewModel.onPasswordChanged("password")
            viewModel.onSignIn()
            advanceUntilIdle()

            // 成功時は認証状態の切り替わりで画面ごと破棄されるため、ボタンを押せる状態には戻さない
            assertTrue(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
            coVerify { signInWithHistoryService.signInWithEmail("test@example.com", "password") }
        }

    @Test
    fun `failed sign in shows error message`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { signInWithHistoryService.signInWithEmail("test@example.com", "wrong") } returns
                Result.failure(Exception("Invalid credentials"))

            viewModel.onEmailChanged("test@example.com")
            viewModel.onPasswordChanged("wrong")
            viewModel.onSignIn()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("Invalid credentials", viewModel.uiState.errorMessage)
        }

    @Test
    fun `webauthn not supported initializes to EMAIL_PASSWORD mode`() {
        val viewModel = createViewModel(webAuthnSupported = false)

        assertEquals(LoginMode.EMAIL_PASSWORD, viewModel.uiState.loginMode)
        assertFalse(viewModel.uiState.isWebAuthnSupported)
    }

    @Test
    fun `webauthn supported initializes to PASSKEY mode`() {
        val viewModel = createViewModel(webAuthnSupported = true)

        assertEquals(LoginMode.PASSKEY, viewModel.uiState.loginMode)
        assertTrue(viewModel.uiState.isWebAuthnSupported)
    }

    @Test
    fun `successful passkey sign in sets signedInViaPasskey`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { passkeyRepository.authenticateWithPasskey() } returns
                Result.success("custom-token")
            coEvery { signInWithHistoryService.signInWithCustomToken("custom-token") } returns Result.success(Unit)

            viewModel.onPasskeySignIn()
            advanceUntilIdle()

            assertTrue(authStateHolder.signedInViaPasskey)
            assertTrue(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
            coVerify { signInWithHistoryService.signInWithCustomToken("custom-token") }
        }

    @Test
    fun `failed custom token sign in shows error`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { passkeyRepository.authenticateWithPasskey() } returns
                Result.success("custom-token")
            coEvery { signInWithHistoryService.signInWithCustomToken("custom-token") } returns
                Result.failure(Exception("Token rejected"))

            viewModel.onPasskeySignIn()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("Token rejected", viewModel.uiState.errorMessage)
        }

    @Test
    fun `failed passkey authentication shows error`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { passkeyRepository.authenticateWithPasskey() } returns
                Result.failure(Exception("Passkey failed"))

            viewModel.onPasskeySignIn()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("Passkey failed", viewModel.uiState.errorMessage)
            assertFalse(authStateHolder.signedInViaPasskey)
        }
}
