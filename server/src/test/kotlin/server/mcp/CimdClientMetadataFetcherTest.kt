package server.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CimdClientMetadataFetcherTest {
    private val clientId = "https://claude.ai/oauth/claude-code-client-metadata"

    private fun fetcher(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
        onRequest: () -> Unit = {},
    ) = CimdClientMetadataFetcher(
        HttpClient(
            MockEngine {
                onRequest()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { expectSuccess = false },
    )

    @Test
    fun readsClientName() =
        runTest {
            val fetcher = fetcher(body = """{"client_id":"$clientId","client_name":"Claude Code","redirect_uris":[]}""")

            assertEquals("Claude Code", fetcher.fetchClientName(clientId))
        }

    @Test
    fun rejectsDocumentForAnotherClientId() =
        runTest {
            val fetcher = fetcher(body = """{"client_id":"https://evil.example.com/client","client_name":"Claude Code"}""")

            assertNull(fetcher.fetchClientName(clientId))
        }

    @Test
    fun returnsNullForErrorStatusOrInvalidBody() =
        runTest {
            assertNull(fetcher(status = HttpStatusCode.NotFound, body = "{}").fetchClientName(clientId))
            assertNull(fetcher(body = "not json").fetchClientName(clientId))
            assertNull(fetcher(body = """{"client_id":"$clientId"}""").fetchClientName(clientId))
            assertNull(fetcher(body = """{"client_id":"$clientId","client_name":42}""").fetchClientName(clientId))
        }

    @Test
    fun rejectsTooLargeBody() =
        runTest {
            val padding = "x".repeat(CimdClientMetadataFetcher.MAX_BODY_BYTES.toInt())
            val fetcher = fetcher(body = """{"client_id":"$clientId","client_name":"Claude Code","pad":"$padding"}""")

            assertNull(fetcher.fetchClientName(clientId))
        }

    @Test
    fun rejectsTooLargeBodyWithoutContentLength() =
        runTest {
            val padding = "x".repeat(CimdClientMetadataFetcher.MAX_BODY_BYTES.toInt())
            val body = """{"client_id":"$clientId","client_name":"Claude Code","pad":"$padding"}""".encodeToByteArray()
            // 大きさを宣言しない応答でも、読みながら上限で打ち切る
            val fetcher =
                CimdClientMetadataFetcher(
                    HttpClient(
                        MockEngine {
                            respond(
                                ByteReadChannel(body),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { expectSuccess = false },
                )

            assertNull(fetcher.fetchClientName(clientId))
        }

    @Test
    fun readsClientNameWithoutContentLength() =
        runTest {
            val body = """{"client_id":"$clientId","client_name":"Claude Code"}""".encodeToByteArray()
            val fetcher =
                CimdClientMetadataFetcher(
                    HttpClient(
                        MockEngine {
                            respond(
                                ByteReadChannel(body),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    ) { expectSuccess = false },
                )

            assertEquals("Claude Code", fetcher.fetchClientName(clientId))
        }

    @Test
    fun doesNotFetchNonHttpsOrIpLiteralUrls() =
        runTest {
            var requests = 0
            val fetcher = fetcher(body = "{}") { requests++ }

            listOf(
                "http://claude.ai/oauth/claude-code-client-metadata",
                "https://127.0.0.1/client",
                "https://[::1]/client",
                "https://user:pass@claude.ai/client",
                "client_01",
            ).forEach { assertNull(fetcher.fetchClientName(it)) }

            assertEquals(0, requests)
        }

    @Test
    fun clientNameIsSanitizedForDisplay() {
        val name = CimdClientMetadataFetcher.parseClientName(clientId, """{"client_id":"$clientId","client_name":"  Claude\nCode‮  "}""")
        assertEquals("ClaudeCode", name)

        val long = "あ".repeat(CimdClientMetadataFetcher.MAX_CLIENT_NAME_LENGTH + 10)
        val truncated = CimdClientMetadataFetcher.parseClientName(clientId, """{"client_id":"$clientId","client_name":"$long"}""")
        assertEquals(CimdClientMetadataFetcher.MAX_CLIENT_NAME_LENGTH, truncated?.length)

        assertNull(CimdClientMetadataFetcher.parseClientName(clientId, """{"client_id":"$clientId","client_name":" \n "}"""))
    }

    @Test
    fun fetchableUrlRequiresHttpsHostName() {
        assertTrue(CimdClientMetadataFetcher.isFetchableUrl(clientId))
        assertFalse(CimdClientMetadataFetcher.isFetchableUrl("https://10.0.0.1/client"))
        assertFalse(CimdClientMetadataFetcher.isFetchableUrl("https://[::1]/client"))
        // OkHttp は数字とドットだけのホストを IP とみなし、名前解決を経ずに接続する
        assertFalse(CimdClientMetadataFetcher.isFetchableUrl("https://127.1/client"))
        assertFalse(CimdClientMetadataFetcher.isFetchableUrl("https://2130706433/client"))
        assertFalse(CimdClientMetadataFetcher.isFetchableUrl("https://127.0.0.1./client"))
        assertFalse(CimdClientMetadataFetcher.isFetchableUrl("ftp://claude.ai/client"))
    }

    @Test
    fun dnsDropsNonPublicAddresses() {
        val delegate =
            Dns { host ->
                when (host) {
                    "mixed.example.com" -> listOf(InetAddress.getByName("10.0.0.1"), InetAddress.getByName("93.184.216.34"))
                    else -> listOf(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("169.254.169.254"))
                }
            }
        val dns = PublicAddressDns(delegate)

        assertEquals(listOf(InetAddress.getByName("93.184.216.34")), dns.lookup("mixed.example.com"))
        // すべて内部向けなら解決できなかったものとして扱い、接続させない
        assertFailsWith<UnknownHostException> { dns.lookup("internal.example.com") }
    }
}
