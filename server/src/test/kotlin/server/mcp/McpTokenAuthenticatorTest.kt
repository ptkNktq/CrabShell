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

            assertEquals(McpAuthResult.Authenticated(McpPrincipal(uid = "uid1", clientId = "client_01")), principal)
        }

    @Test
    fun disabledOrDeletedUserIsRejected() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returnsMany
                listOf(FirebaseUserStatus.DISABLED, FirebaseUserStatus.NOT_FOUND)

            assertEquals(McpAuthResult.Rejected, authenticator.authenticate("uid1", null))
            assertEquals(McpAuthResult.Rejected, authenticator.authenticate("uid1", null))
        }

    @Test
    fun firebaseFailureIsUnavailableNotRejected() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } throws RuntimeException("Firebase unreachable")

            assertEquals(McpAuthResult.Unavailable, authenticator.authenticate("uid1", null))
        }

    @Test
    fun claimForLogHasNoControlCharactersAndIsTruncated() {
        assertEquals("uid1?a?b", sanitizeClaimForLog("uid1\na\u2028b"))
        assertEquals(128, sanitizeClaimForLog("a".repeat(1000))!!.length)
        assertNull(sanitizeClaimForLog(null))
    }
}
