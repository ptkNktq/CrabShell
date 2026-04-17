package feature.auth

import core.auth.AuthRepository
import core.auth.AuthStateHolder
import core.network.LoginHistoryRepository
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
    private lateinit var loginHistoryRepository: LoginHistoryRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk()
        passkeyRepository = mockk()
        authStateHolder = AuthStateHolder()
        loginHistoryRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(webAuthnSupported: Boolean = true): LoginViewModel {
        every { authRepository.isWebAuthnSupported() } returns webAuthnSupported
        return LoginViewModel(authRepository, passkeyRepository, authStateHolder, loginHistoryRepository)
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
    fun `successful sign in sets isLoading to false`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { authRepository.signIn("test@example.com", "password") } returns Result.success(Unit)

            viewModel.onEmailChanged("test@example.com")
            viewModel.onPasswordChanged("password")
            viewModel.onSignIn()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
            coVerify { loginHistoryRepository.recordLogin("email") }
        }

    @Test
    fun `login history failure does not block sign in`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { authRepository.signIn("test@example.com", "password") } returns Result.success(Unit)
            coEvery { loginHistoryRepository.recordLogin("email") } throws RuntimeException("Network error")

            viewModel.onEmailChanged("test@example.com")
            viewModel.onPasswordChanged("password")
            viewModel.onSignIn()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
        }

    @Test
    fun `failed sign in shows error message`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { authRepository.signIn("test@example.com", "wrong") } returns
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
    fun `passkey sign in with empty email shows error`() {
        val viewModel = createViewModel()
        viewModel.onPasskeySignIn()

        assertEquals("メールアドレスを入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `successful passkey sign in sets signedInViaPasskey`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { passkeyRepository.authenticateWithPasskey("test@example.com") } returns
                Result.success("custom-token")
            coEvery { authRepository.signInWithCustomToken("custom-token") } returns Result.success(Unit)

            viewModel.onEmailChanged("test@example.com")
            viewModel.onPasskeySignIn()
            advanceUntilIdle()

            assertTrue(authStateHolder.signedInViaPasskey)
            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
            coVerify { loginHistoryRepository.recordLogin("passkey") }
        }

    @Test
    fun `failed passkey authentication shows error`() =
        runTest {
            val viewModel = createViewModel()
            coEvery { passkeyRepository.authenticateWithPasskey("test@example.com") } returns
                Result.failure(Exception("Passkey failed"))

            viewModel.onEmailChanged("test@example.com")
            viewModel.onPasskeySignIn()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals("Passkey failed", viewModel.uiState.errorMessage)
            assertFalse(authStateHolder.signedInViaPasskey)
        }
}
