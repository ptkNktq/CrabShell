package server.mcp

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpTokenAuthenticatorTest {
    private val firebaseAuthRepository = mockk<FirebaseAuthRepository>()
    private val authenticator = McpTokenAuthenticator(firebaseAuthRepository)

    @Test
    fun activeUserIsAuthenticated() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE

            val principal = authenticator.authenticate("uid1", "client_01")

            assertEquals(McpPrincipal(uid = "uid1", clientId = "client_01"), principal)
        }

    @Test
    fun disabledOrDeletedUserIsRejected() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returnsMany
                listOf(FirebaseUserStatus.DISABLED, FirebaseUserStatus.NOT_FOUND)

            assertNull(authenticator.authenticate("uid1", null))
            assertNull(authenticator.authenticate("uid1", null))
        }
}
