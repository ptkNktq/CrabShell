package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class McpUserResolverTest {
    private val workOsClient = mockk<WorkOsClient>()
    private val resolver = McpUserResolver(workOsClient)

    @Test
    fun resolvesUidAndCachesIt() =
        runTest {
            coEvery { workOsClient.getExternalId("user_01") } returns "uid1"

            assertEquals("uid1", resolver.resolveUid("user_01"))
            assertEquals("uid1", resolver.resolveUid("user_01"))

            coVerify(exactly = 1) { workOsClient.getExternalId("user_01") }
        }

    @Test
    fun returnsNullWithoutCachingWhenExternalIdIsMissing() =
        runTest {
            coEvery { workOsClient.getExternalId("user_01") } returnsMany listOf(null, "uid1")

            assertNull(resolver.resolveUid("user_01"))
            assertEquals("uid1", resolver.resolveUid("user_01"))
        }

    @Test
    fun propagatesWorkOsFailure() =
        runTest {
            coEvery { workOsClient.getExternalId("user_01") } throws WorkOsApiException("500")

            assertFailsWith<WorkOsApiException> { resolver.resolveUid("user_01") }
        }
}
