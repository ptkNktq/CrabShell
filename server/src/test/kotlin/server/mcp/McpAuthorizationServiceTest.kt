package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class McpAuthorizationServiceTest {
    private val workOsClient = mockk<WorkOsClient>()
    private val service = McpAuthorizationService(workOsClient)

    @Test
    fun completesWithUidAndEmail() =
        runTest {
            coEvery {
                workOsClient.completeExternalAuth("01J3X4Y5Z6", WorkOsExternalUser(id = "uid1", email = "a@example.com"))
            } returns "https://example.authkit.app/consent"

            val result = service.complete(uid = "uid1", email = "a@example.com", externalAuthId = "01J3X4Y5Z6")

            assertEquals(McpAuthorizationResult.Completed("https://example.authkit.app/consent"), result)
        }

    @Test
    fun rejectsInvalidExternalAuthIdWithoutCallingWorkOs() =
        runTest {
            listOf("", "abc/def", "a".repeat(129), "abc def").forEach { id ->
                assertEquals(McpAuthorizationResult.InvalidExternalAuthId, service.complete("uid1", "a@example.com", id))
            }
            coVerify(exactly = 0) { workOsClient.completeExternalAuth(any(), any()) }
        }

    @Test
    fun rejectsUserWithoutEmail() =
        runTest {
            assertEquals(McpAuthorizationResult.MissingEmail, service.complete("uid1", null, "01J3X4Y5Z6"))
            assertEquals(McpAuthorizationResult.MissingEmail, service.complete("uid1", " ", "01J3X4Y5Z6"))
            coVerify(exactly = 0) { workOsClient.completeExternalAuth(any(), any()) }
        }

    @Test
    fun reportsUpstreamFailure() =
        runTest {
            coEvery { workOsClient.completeExternalAuth(any(), any()) } throws WorkOsApiException("500")

            assertEquals(McpAuthorizationResult.UpstreamFailure, service.complete("uid1", "a@example.com", "01J3X4Y5Z6"))
        }

    @Test
    fun rejectsNonHttpsRedirectUri() =
        runTest {
            listOf("javascript:alert(1)", "http://example.authkit.app/consent", "https:foo", "not a uri").forEach { redirectUri ->
                coEvery { workOsClient.completeExternalAuth(any(), any()) } returns redirectUri

                assertEquals(McpAuthorizationResult.UpstreamFailure, service.complete("uid1", "a@example.com", "01J3X4Y5Z6"))
            }
        }
}
