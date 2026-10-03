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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
    fun getExternalIdReturnsExternalId() =
        runTest {
            val client = client { respondJson("""{"id":"user_01","external_id":"uid1","email":"a@example.com"}""") }

            assertEquals("uid1", client.getExternalId("user_01"))
            assertEquals("https://api.workos.test/user_management/users/user_01", requests.single().url.toString())
            assertEquals("Bearer sk_test", requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun getExternalIdReturnsNullWhenMissingOrNotFound() =
        runTest {
            assertNull(client { respondJson("""{"id":"user_01","external_id":null}""") }.getExternalId("user_01"))
            assertNull(client { respondJson("""{"id":"user_01","external_id":""}""") }.getExternalId("user_01"))
            assertNull(client { respondJson("""{"message":"not found"}""", HttpStatusCode.NotFound) }.getExternalId("user_01"))
        }

    @Test
    fun getExternalIdThrowsOnOtherFailures() =
        runTest {
            val client = client { respondJson("""{"message":"error"}""", HttpStatusCode.InternalServerError) }

            assertFailsWith<WorkOsApiException> { client.getExternalId("user_01") }
        }

    @Test
    fun getExternalIdEncodesUserIdInPath() =
        runTest {
            client { respondJson("""{"external_id":"uid1"}""") }.getExternalId("../events")

            assertEquals("https://api.workos.test/user_management/users/..%2Fevents", requests.single().url.toString())
        }
}
