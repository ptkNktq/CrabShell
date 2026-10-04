package server.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkOsHttpClientTest {
    private val config = McpConfig.parse(apiKey = "sk_test", authKitDomain = "example.authkit.app", appUrl = "https://crab.example.com")
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): WorkOsHttpClient =
        WorkOsHttpClient(
            config = config,
            client =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            requests += request
                            handler(request)
                        }
                    }
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                },
            baseUrl = "https://api.workos.test",
        )

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun completeExternalAuthSendsUserAndReturnsRedirectUri() =
        runTest {
            val client = client { respondJson("""{"redirect_uri":"https://example.authkit.app/consent","extra":1}""") }

            val redirectUri = client.completeExternalAuth("01J3X4Y5Z6", WorkOsExternalUser(id = "uid1", email = "a@example.com"))

            assertEquals("https://example.authkit.app/consent", redirectUri)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://api.workos.test/authkit/oauth2/complete", request.url.toString())
            assertEquals("Bearer sk_test", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("01J3X4Y5Z6", body["external_auth_id"]!!.jsonPrimitive.content)
            val user = body["user"]!!.jsonObject
            assertEquals("uid1", user["id"]!!.jsonPrimitive.content)
            assertEquals("a@example.com", user["email"]!!.jsonPrimitive.content)
        }

    @Test
    fun completeExternalAuthThrowsOnFailure() =
        runTest {
            val client = client { respondJson("""{"message":"invalid"}""", HttpStatusCode.BadRequest) }

            assertFailsWith<WorkOsApiException> {
                client.completeExternalAuth("01J3X4Y5Z6", WorkOsExternalUser(id = "uid1", email = "a@example.com"))
            }
        }

    @Test
    fun completeExternalAuthWrapsConnectionAndResponseFailures() =
        runTest {
            listOf(
                client { throw IOException("connection reset") },
                client { respondJson("""{"unexpected":true}""") },
            ).forEach { client ->
                assertFailsWith<WorkOsApiException> {
                    client.completeExternalAuth("01J3X4Y5Z6", WorkOsExternalUser(id = "uid1", email = "a@example.com"))
                }
            }
        }
}
