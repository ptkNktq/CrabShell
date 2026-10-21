package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class McpTokenAuthenticatorTest {
    private val firebaseAuthRepository = mockk<FirebaseAuthRepository>()
    private val connectionService = mockk<McpConnectionService>()
    private val authenticator = McpTokenAuthenticator(firebaseAuthRepository, connectionService)

    @Test
    fun activeUserIsAuthenticated() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE
            coEvery { connectionService.checkAndRecordUse("uid1", "client_01") } returns McpConnectionUse.ALLOWED

            val principal = authenticator.authenticate("uid1", "client_01")

            assertEquals(McpAuthResult.Authenticated(McpPrincipal(uid = "uid1", clientId = "client_01")), principal)
        }

    @Test
    fun disabledOrDeletedUserIsRejected() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returnsMany
                listOf(FirebaseUserStatus.DISABLED, FirebaseUserStatus.NOT_FOUND)

            assertEquals(McpAuthResult.Rejected, authenticator.authenticate("uid1", "client_01"))
            assertEquals(McpAuthResult.Rejected, authenticator.authenticate("uid1", "client_01"))
            // 利用できないユーザーの利用は記録しない
            coVerify(exactly = 0) { connectionService.checkAndRecordUse(any(), any()) }
        }

    @Test
    fun tokenWithoutClientIdIsRejected() =
        runTest {
            assertEquals(McpAuthResult.Rejected, authenticator.authenticate("uid1", null))
            assertEquals(McpAuthResult.Rejected, authenticator.authenticate("uid1", ""))
        }

    @Test
    fun disabledConnectionIsDisabled() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE
            coEvery { connectionService.checkAndRecordUse("uid1", "client_01") } returns McpConnectionUse.DISABLED

            assertEquals(McpAuthResult.Disabled, authenticator.authenticate("uid1", "client_01"))
        }

    @Test
    fun firebaseFailureIsUnavailableNotRejected() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } throws RuntimeException("Firebase unreachable")

            assertEquals(McpAuthResult.Unavailable, authenticator.authenticate("uid1", "client_01"))
        }

    @Test
    fun connectionCheckFailureIsUnavailableNotRejected() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE
            coEvery { connectionService.checkAndRecordUse("uid1", "client_01") } throws RuntimeException("Firestore unreachable")

            assertEquals(McpAuthResult.Unavailable, authenticator.authenticate("uid1", "client_01"))
        }
}
