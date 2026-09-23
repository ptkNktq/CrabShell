package feature.auth

import core.auth.AuthRepository
import core.network.LoginHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import model.LoginMethod
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignInServiceTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var loginHistoryRepository: LoginHistoryRepository

    @BeforeTest
    fun setUp() {
        authRepository = mockk()
        loginHistoryRepository = mockk(relaxed = true)
    }

    /** アプリ全体スコープ相当。呼び出し元（ViewModel 相当）のスコープとは独立させる。 */
    private fun createService(): SignInService =
        SignInService(
            authRepository,
            loginHistoryRepository,
            CoroutineScope(SupervisorJob() + testDispatcher),
        )

    @Test
    fun `successful email sign in records login history`() =
        runTest(testDispatcher) {
            coEvery { authRepository.signIn("a@example.com", "pw") } returns Result.success(Unit)
            val service = createService()

            val result = service.signInWithEmail("a@example.com", "pw")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { loginHistoryRepository.recordLogin(LoginMethod.EMAIL) }
        }

    @Test
    fun `successful custom token sign in records passkey login history`() =
        runTest(testDispatcher) {
            coEvery { authRepository.signInWithCustomToken("token") } returns Result.success(Unit)
            val service = createService()

            val result = service.signInWithCustomToken("token")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { loginHistoryRepository.recordLogin(LoginMethod.PASSKEY) }
        }

    @Test
    fun `failed sign in does not record login history`() =
        runTest(testDispatcher) {
            val error = Exception("Invalid credentials")
            coEvery { authRepository.signIn("a@example.com", "wrong") } returns Result.failure(error)
            val service = createService()

            val result = service.signInWithEmail("a@example.com", "wrong")
            advanceUntilIdle()

            assertEquals(error, result.exceptionOrNull())
            coVerify(exactly = 0) { loginHistoryRepository.recordLogin(any()) }
        }

    @Test
    fun `login history failure does not affect sign in result`() =
        runTest(testDispatcher) {
            coEvery { authRepository.signIn("a@example.com", "pw") } returns Result.success(Unit)
            coEvery { loginHistoryRepository.recordLogin(LoginMethod.EMAIL) } throws RuntimeException("Network error")
            val service = createService()

            val result = service.signInWithEmail("a@example.com", "pw")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `sign in and history recording complete even if caller is cancelled`() =
        runTest(testDispatcher) {
            // サインイン完了前に呼び出し元（LoginViewModel の viewModelScope 相当）が破棄されるケース
            val signInGate = CompletableDeferred<Unit>()
            coEvery { authRepository.signIn("a@example.com", "pw") } coAnswers {
                signInGate.await()
                Result.success(Unit)
            }
            val service = createService()
            val callerScope = CoroutineScope(SupervisorJob() + testDispatcher)

            callerScope.launch { service.signInWithEmail("a@example.com", "pw") }
            advanceUntilIdle()
            callerScope.cancel()
            signInGate.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { loginHistoryRepository.recordLogin(LoginMethod.EMAIL) }
        }
}
