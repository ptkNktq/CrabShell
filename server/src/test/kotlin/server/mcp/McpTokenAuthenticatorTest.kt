package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class McpTokenAuthenticatorTest {
    private val firebaseAuthRepository = mockk<FirebaseAuthRepository>()
    private val connectionService = mockk<McpConnectionService>()
    private val now = Instant.parse("2026-10-24T00:00:00Z")
    private val disabledConnectionGuard = McpDisabledConnectionGuard(now = { now })
    private val authenticator = McpTokenAuthenticator(firebaseAuthRepository, connectionService, disabledConnectionGuard)

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
    fun repeatedDisabledConnectionBlocksUserWithoutQuerying() =
        runTest {
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE
            coEvery { connectionService.checkAndRecordUse("uid1", "client_01") } returns McpConnectionUse.DISABLED

            repeat(McpDisabledConnectionGuard.MAX_REJECTIONS + 1) {
                assertEquals(McpAuthResult.Disabled, authenticator.authenticate("uid1", "client_01"))
            }

            // 拒否が続いたユーザーは、他のアプリからのリクエストも Firebase・Firestore に問い合わせずに止める
            val result = authenticator.authenticate("uid1", "client_02")
            assertIs<McpAuthResult.Blocked>(result)
            assertEquals(McpDisabledConnectionGuard.BLOCK_DURATION, result.remaining)
            verify(exactly = McpDisabledConnectionGuard.MAX_REJECTIONS + 1) { firebaseAuthRepository.getUserStatus("uid1") }
            coVerify(exactly = 0) { connectionService.checkAndRecordUse("uid1", "client_02") }
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
