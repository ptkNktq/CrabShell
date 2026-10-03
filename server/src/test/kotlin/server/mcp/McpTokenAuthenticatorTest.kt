package server.mcp

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpTokenAuthenticatorTest {
    private val workOsClient = mockk<WorkOsClient>()
    private val firebaseAuthRepository = mockk<FirebaseAuthRepository>()
    private val authenticator = McpTokenAuthenticator(McpUserResolver(workOsClient), firebaseAuthRepository)

    @Test
    fun activeUserIsAuthenticated() =
        runTest {
            coEvery { workOsClient.getExternalId("user_01") } returns "uid1"
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE

            val principal = authenticator.authenticate("user_01", "client_01")

            assertEquals(McpPrincipal(uid = "uid1", workOsUserId = "user_01", clientId = "client_01"), principal)
        }

    @Test
    fun disabledOrDeletedUserIsRejected() =
        runTest {
            coEvery { workOsClient.getExternalId("user_01") } returns "uid1"
            every { firebaseAuthRepository.getUserStatus("uid1") } returnsMany
                listOf(FirebaseUserStatus.DISABLED, FirebaseUserStatus.NOT_FOUND)

            assertNull(authenticator.authenticate("user_01", null))
            assertNull(authenticator.authenticate("user_01", null))
        }

    @Test
    fun userWithoutExternalIdIsRejectedWithoutCheckingFirebase() =
        runTest {
            coEvery { workOsClient.getExternalId("user_01") } returns null

            assertNull(authenticator.authenticate("user_01", null))
            verify(exactly = 0) { firebaseAuthRepository.getUserStatus(any()) }
        }
}
