package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import model.McpConnection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpConnectionServiceTest {
    private val now = Instant.parse("2026-10-21T00:00:00Z")
    private val repository = mockk<McpConnectionRepository>(relaxUnitFun = true)
    private val fetcher = mockk<ClientMetadataFetcher>()
    private val service = McpConnectionService(repository, fetcher, Clock.fixed(now, ZoneOffset.UTC))

    private val clientId = "https://claude.ai/oauth/claude-code-client-metadata"
    private val connectionId = mcpConnectionId(clientId)

    private fun record(
        lastUsedAt: Instant = now.minus(Duration.ofDays(1)),
        disabledAt: Instant? = null,
        clientName: String? = "Claude Code",
        clientNameFetchedAt: Instant? = now.minus(Duration.ofDays(1)),
    ) = McpConnectionRecord(
        id = connectionId,
        clientId = clientId,
        clientName = clientName,
        clientNameFetchedAt = clientNameFetchedAt,
        firstUsedAt = now.minus(Duration.ofDays(10)),
        lastUsedAt = lastUsedAt,
        disabledAt = disabledAt,
    )

    @Test
    fun connectionIdIsSha256HexOfClientId() {
        assertTrue(isMcpConnectionId(connectionId))
        assertEquals(connectionId, mcpConnectionId(clientId))
        assertTrue(!isMcpConnectionId("../other"))
        assertTrue(!isMcpConnectionId(connectionId.uppercase()))
    }

    @Test
    fun firstUseCreatesRecord() =
        runTest {
            coEvery { repository.find("uid1", connectionId) } returns null

            assertEquals(McpConnectionUse.ALLOWED, service.checkAndRecordUse("uid1", clientId))

            coVerify {
                repository.createIfAbsent(
                    "uid1",
                    McpConnectionRecord(connectionId, clientId, null, null, firstUsedAt = now, lastUsedAt = now, disabledAt = null),
                )
            }
        }

    @Test
    fun lastUsedAtIsUpdatedOnlyAfterInterval() =
        runTest {
            coEvery { repository.find("uid1", connectionId) } returnsMany
                listOf(
                    record(lastUsedAt = now.minus(McpConnectionService.LAST_USED_UPDATE_INTERVAL).plusSeconds(1)),
                    record(lastUsedAt = now.minus(McpConnectionService.LAST_USED_UPDATE_INTERVAL)),
                )

            service.checkAndRecordUse("uid1", clientId)
            coVerify(exactly = 0) { repository.updateLastUsedAt(any(), any(), any()) }

            service.checkAndRecordUse("uid1", clientId)
            coVerify(exactly = 1) { repository.updateLastUsedAt("uid1", connectionId, now) }
        }

    @Test
    fun disabledConnectionIsNotRecorded() =
        runTest {
            coEvery { repository.find("uid1", connectionId) } returns record(lastUsedAt = now.minus(Duration.ofDays(30)), disabledAt = now)

            assertEquals(McpConnectionUse.DISABLED, service.checkAndRecordUse("uid1", clientId))

            coVerify(exactly = 0) { repository.updateLastUsedAt(any(), any(), any()) }
        }

    @Test
    fun readFailureIsPropagated() =
        runTest {
            coEvery { repository.find("uid1", connectionId) } throws RuntimeException("Firestore unreachable")

            assertFailsWith<RuntimeException> { service.checkAndRecordUse("uid1", clientId) }
        }

    @Test
    fun writeFailureDoesNotBlockUse() =
        runTest {
            coEvery { repository.find("uid1", connectionId) } returns null
            coEvery { repository.createIfAbsent(any(), any()) } throws RuntimeException("Firestore unreachable")

            assertEquals(McpConnectionUse.ALLOWED, service.checkAndRecordUse("uid1", clientId))
        }

    @Test
    fun listUsesCachedClientNameWithinRefreshInterval() =
        runTest {
            coEvery { repository.list("uid1", McpConnectionService.MAX_CONNECTIONS) } returns listOf(record())

            val connections = service.list("uid1")

            assertEquals(
                listOf(
                    McpConnection(
                        id = connectionId,
                        clientName = "Claude Code",
                        firstUsedAt = "2026-10-11T00:00:00Z",
                        lastUsedAt = "2026-10-20T00:00:00Z",
                    ),
                ),
                connections,
            )
            coVerify(exactly = 0) { fetcher.fetchClientName(any()) }
        }

    @Test
    fun listRefreshesMissingOrStaleClientName() =
        runTest {
            coEvery { repository.list("uid1", any()) } returns
                listOf(record(clientName = null, clientNameFetchedAt = null))
            coEvery { fetcher.fetchClientName(clientId) } returns "Claude Code"

            assertEquals("Claude Code", service.list("uid1").single().clientName)
            coVerify { repository.updateClientName("uid1", connectionId, "Claude Code", now) }
        }

    @Test
    fun failedRefreshKeepsPreviousNameAndFallsBackToHost() =
        runTest {
            val stale = now.minus(McpConnectionService.CLIENT_NAME_REFRESH_INTERVAL)
            coEvery { repository.list("uid1", any()) } returns
                listOf(
                    record(clientName = "Claude Code", clientNameFetchedAt = stale),
                    record(clientName = null, clientNameFetchedAt = stale).copy(id = "other", clientId = "https://example.com/client.json"),
                )
            coEvery { fetcher.fetchClientName(any()) } returns null

            val names = service.list("uid1").map { it.clientName }

            // 取り直せなくても前回の名前を残し、名前がなければホスト名を出す
            assertEquals(listOf("Claude Code", "example.com"), names)
            coVerify { repository.updateClientName("uid1", connectionId, "Claude Code", now) }
        }

    @Test
    fun missingClientNameIsRetriedSoonerThanCachedName() =
        runTest {
            val retryDue = now.minus(McpConnectionService.CLIENT_NAME_RETRY_INTERVAL)
            val other =
                record(
                    clientName = null,
                    clientNameFetchedAt = retryDue,
                ).copy(id = "other", clientId = "https://example.com/client.json")
            coEvery { repository.list("uid1", any()) } returns
                listOf(record(clientName = "Claude Code", clientNameFetchedAt = retryDue), other)
            coEvery { fetcher.fetchClientName("https://example.com/client.json") } returns "Example"

            assertEquals(listOf("Claude Code", "Example"), service.list("uid1").map { it.clientName })
            // 名前が取れているものは 7 日間取り直さない
            coVerify(exactly = 0) { fetcher.fetchClientName(clientId) }
        }

    @Test
    fun slowMetadataFetchDoesNotBlockList() =
        runTest {
            val other =
                record(
                    clientName = null,
                    clientNameFetchedAt = null,
                ).copy(id = "other", clientId = "https://example.com/client.json")
            coEvery { repository.list("uid1", any()) } returns listOf(record(clientName = null, clientNameFetchedAt = null), other)
            coEvery { fetcher.fetchClientName(clientId) } coAnswers {
                delay(McpConnectionService.METADATA_FETCH_DEADLINE_MILLIS * 10)
                "Claude Code"
            }
            coEvery { fetcher.fetchClientName("https://example.com/client.json") } returns "Example"

            val names = service.list("uid1").map { it.clientName }

            // 間に合わなかったものはホスト名で返し、保存もしない
            assertEquals(listOf("claude.ai", "Example"), names)
            assertEquals(McpConnectionService.METADATA_FETCH_DEADLINE_MILLIS, testScheduler.currentTime)
            coVerify(exactly = 0) { repository.updateClientName("uid1", connectionId, any(), any()) }
            coVerify { repository.updateClientName("uid1", "other", "Example", now) }
        }

    @Test
    fun metadataFetchTimesOutBeforeListDeadline() {
        // 応答しないホストは締め切りより先に取得のタイムアウトで失敗させ、失敗として保存させる
        // （Ktor の requestTimeoutMillis は接続を含むリクエスト全体の上限）
        assertTrue(CimdClientMetadataFetcher.REQUEST_TIMEOUT_MILLIS < McpConnectionService.METADATA_FETCH_DEADLINE_MILLIS)
    }

    @Test
    fun setDisabledReturnsUpdatedConnectionOrNull() =
        runTest {
            coEvery { repository.updateDisabledAt("uid1", connectionId, now) } returns record(disabledAt = now)
            coEvery { repository.updateDisabledAt("uid1", "missing", null) } returns null

            assertEquals(now.toString(), service.setDisabled("uid1", connectionId, disabled = true)?.disabledAt)
            assertNull(service.setDisabled("uid1", "missing", disabled = false))
        }

    @Test
    fun fallbackClientNameUsesHostOrRawValue() {
        assertEquals("claude.ai", McpConnectionService.fallbackClientName(clientId))
        assertEquals("client_01", McpConnectionService.fallbackClientName("client_01"))
    }
}
