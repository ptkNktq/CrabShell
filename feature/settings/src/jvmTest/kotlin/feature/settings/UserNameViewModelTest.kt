package feature.settings

import core.network.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class UserNameViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepository: UserRepository

    private val testUsers =
        listOf(
            User(uid = "u1", email = "alice@example.com", displayName = "Alice"),
            User(uid = "u2", email = "bob@example.com", displayName = "Bob"),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UserNameViewModel {
        coEvery { userRepository.getUsers() } returns testUsers
        return UserNameViewModel(userRepository)
    }

    @Test
    fun `init loads users`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(testUsers, viewModel.uiState.users)
        }

    @Test
    fun `init load failure keeps empty list`() =
        runTest {
            coEvery { userRepository.getUsers() } throws RuntimeException("error")
            val viewModel = UserNameViewModel(userRepository)
            advanceUntilIdle()

            assertEquals(emptyList(), viewModel.uiState.users)
        }

    @Test
    fun `update display name success shows message and updates list`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val updatedUser = User(uid = "u1", email = "alice@example.com", displayName = "Alice2")
            coEvery { userRepository.updateDisplayName("u1", "Alice2") } returns updatedUser

            viewModel.onUpdateDisplayName("u1", "Alice2")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存しました", viewModel.uiState.message)
            assertEquals(
                "Alice2",
                viewModel.uiState.users
                    .first { it.uid == "u1" }
                    .displayName,
            )
            assertEquals(
                "Bob",
                viewModel.uiState.users
                    .first { it.uid == "u2" }
                    .displayName,
            )
        }

    @Test
    fun `update display name failure shows error`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            coEvery { userRepository.updateDisplayName("u1", "New") } throws RuntimeException("save error")

            viewModel.onUpdateDisplayName("u1", "New")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("保存に失敗しました: save error", viewModel.uiState.message)
        }

    @Test
    fun `update display name sets isSaving during save`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val updatedUser = User(uid = "u1", email = "alice@example.com", displayName = "New")
            coEvery { userRepository.updateDisplayName("u1", "New") } returns updatedUser

            viewModel.onUpdateDisplayName("u1", "New")
            assertNull(viewModel.uiState.message)

            advanceUntilIdle()
            assertFalse(viewModel.uiState.isSaving)
        }
}
