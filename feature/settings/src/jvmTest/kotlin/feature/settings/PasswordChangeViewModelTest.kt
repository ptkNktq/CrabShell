package feature.settings

import core.auth.AuthRepository
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordChangeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: PasswordChangeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk()
        viewModel = PasswordChangeViewModel(authRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty current password shows error`() {
        viewModel.onNewPasswordChanged("newpass")
        viewModel.onConfirmPasswordChanged("newpass")
        viewModel.onChangePassword()

        assertEquals("すべての項目を入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `empty new password shows error`() {
        viewModel.onCurrentPasswordChanged("current")
        viewModel.onConfirmPasswordChanged("newpass")
        viewModel.onChangePassword()

        assertEquals("すべての項目を入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `empty confirm password shows error`() {
        viewModel.onCurrentPasswordChanged("current")
        viewModel.onNewPasswordChanged("newpass")
        viewModel.onChangePassword()

        assertEquals("すべての項目を入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `mismatched passwords shows error`() {
        viewModel.onCurrentPasswordChanged("current")
        viewModel.onNewPasswordChanged("newpass1")
        viewModel.onConfirmPasswordChanged("newpass2")
        viewModel.onChangePassword()

        assertEquals("新しいパスワードが一致しません", viewModel.uiState.errorMessage)
    }

    @Test
    fun `password shorter than 6 chars shows error`() {
        viewModel.onCurrentPasswordChanged("current")
        viewModel.onNewPasswordChanged("12345")
        viewModel.onConfirmPasswordChanged("12345")
        viewModel.onChangePassword()

        assertEquals("パスワードは6文字以上で入力してください", viewModel.uiState.errorMessage)
    }

    @Test
    fun `successful password change clears fields and shows success`() =
        runTest {
            coEvery { authRepository.changePassword("current", "newpass") } returns Result.success(Unit)

            viewModel.onCurrentPasswordChanged("current")
            viewModel.onNewPasswordChanged("newpass")
            viewModel.onConfirmPasswordChanged("newpass")
            viewModel.onChangePassword()
            advanceUntilIdle()

            val state = viewModel.uiState
            assertFalse(state.isLoading)
            assertEquals("パスワードを変更しました", state.successMessage)
            assertNull(state.errorMessage)
            assertEquals("", state.currentPassword)
            assertEquals("", state.newPassword)
            assertEquals("", state.confirmPassword)
        }

    @Test
    fun `failed password change shows error message`() =
        runTest {
            coEvery { authRepository.changePassword("current", "newpass") } returns
                Result.failure(Exception("auth error"))

            viewModel.onCurrentPasswordChanged("current")
            viewModel.onNewPasswordChanged("newpass")
            viewModel.onConfirmPasswordChanged("newpass")
            viewModel.onChangePassword()
            advanceUntilIdle()

            val state = viewModel.uiState
            assertFalse(state.isLoading)
            assertEquals("auth error", state.errorMessage)
            assertNull(state.successMessage)
        }

    @Test
    fun `input change clears error and success messages`() {
        viewModel.onChangePassword()
        assertEquals("すべての項目を入力してください", viewModel.uiState.errorMessage)

        viewModel.onCurrentPasswordChanged("x")
        assertNull(viewModel.uiState.errorMessage)
        assertNull(viewModel.uiState.successMessage)
    }
}
