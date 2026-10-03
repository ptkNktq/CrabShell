package server.mcp

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.FeedingLog
import model.Pet
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import server.feeding.FeedingRepository
import server.pet.PetRepository
import server.ratelimit.RateLimitNames
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class McpRoutesTest {
    private val config = McpConfig.parse(apiKey = "sk_test", authKitDomain = "example.authkit.app", appUrl = "https://crab.example.com")

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey
    private val jwkProvider =
        JwkProvider { kid ->
            val encoder = Base64.getUrlEncoder().withoutPadding()
            Jwk.fromValues(
                mapOf(
                    "kid" to kid,
                    "kty" to "RSA",
                    "alg" to "RS256",
                    "use" to "sig",
                    "n" to
                        encoder.encodeToString(
                            publicKey.modulus
                                .toByteArray()
                                .dropWhile { it == 0.toByte() }
                                .toByteArray(),
                        ),
                    "e" to encoder.encodeToString(publicKey.publicExponent.toByteArray()),
                ),
            )
        }

    private val workOsClient = mockk<WorkOsClient>()
    private val firebaseAuthRepository = mockk<FirebaseAuthRepository>()
    private val feedingRepository = mockk<FeedingRepository>(relaxUnitFun = true)
    private val petRepository = mockk<PetRepository>()

    init {
        coEvery { workOsClient.getExternalId("user_01") } returns "uid1"
        every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.ACTIVE
        coEvery { petRepository.getPetsForMember("uid1") } returns listOf(Pet(id = "pet1", name = "ぬい"))
        coEvery { feedingRepository.getFeedingLog("pet1", any()) } answers { FeedingLog(date = secondArg()) }
    }

    @Serializable
    private data class Sample(
        val value: String?,
    )

    private fun token(
        issuer: String = config.issuer,
        audience: String = config.resource,
        expiresAt: Instant = Instant.now().plusSeconds(300),
    ): String =
        JWT
            .create()
            .withKeyId("key1")
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject("user_01")
            .withClaim("client_id", "client_01")
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.RSA256(publicKey, privateKey))

    private fun ApplicationTestBuilder.setUp() {
        application {
            install(Koin) {
                modules(
                    module {
                        single { McpServerFactory(FeedingMcpTools(feedingRepository, petRepository)) }
                    },
                )
            }
            install(Authentication) {
                mcpJwt(config, jwkProvider, McpTokenAuthenticator(McpUserResolver(workOsClient), firebaseAuthRepository))
            }
            install(RateLimit) {
                register(RateLimitNames.MCP) {
                    rateLimiter(limit = 60, refillPeriod = 60.seconds)
                    requestKey { call -> call.mcpPrincipal.uid }
                }
            }
            // 本番（Application.module）と同じく、ContentNegotiation はルーティングのルートに入れる
            routing {
                install(ContentNegotiation) { json() }
                route("/api") { get("/sample") { call.respond(Sample(value = null)) } }
                mcpRoutes(config)
            }
        }
    }

    private suspend fun ApplicationTestBuilder.rpc(
        body: String,
        token: String? = token(),
    ): HttpResponse =
        client.post(MCP_PATH) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            setBody(body)
        }

    private fun callTool(
        name: String,
        arguments: String,
    ) = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}"""

    private fun HttpResponse.resultOf(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject["result"]!!.jsonObject

    @Test
    fun requestWithoutTokenGets401WithResourceMetadata() =
        testApplication {
            setUp()

            val response = rpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""", token = null)

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(
                "Bearer resource_metadata=\"https://crab.example.com/.well-known/oauth-protected-resource/mcp\"",
                response.headers[HttpHeaders.WWWAuthenticate],
            )
        }

    @Test
    fun tokenForAnotherResourceOrIssuerIsRejected() =
        testApplication {
            setUp()

            listOf(
                token(audience = "https://other.example.com/mcp"),
                token(issuer = "https://evil.authkit.app"),
                token(expiresAt = Instant.now().minusSeconds(300)),
            ).forEach { invalid ->
                val response = rpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""", token = invalid)

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                assertTrue(response.headers[HttpHeaders.WWWAuthenticate]!!.startsWith("Bearer error=\"invalid_token\""))
            }
        }

    @Test
    fun disabledUserIsRejected() =
        testApplication {
            setUp()
            every { firebaseAuthRepository.getUserStatus("uid1") } returns FirebaseUserStatus.DISABLED

            assertEquals(HttpStatusCode.Unauthorized, rpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""").status)
        }

    @Test
    fun protectedResourceMetadataPointsToAuthKit() =
        testApplication {
            setUp()

            val body = Json.parseToJsonElement(client.get(MCP_RESOURCE_METADATA_PATH).bodyAsText()).jsonObject

            assertEquals("https://crab.example.com/mcp", body["resource"]!!.jsonPrimitive.content)
            assertEquals(
                "https://example.authkit.app",
                body["authorization_servers"]!!
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun toolsListReturnsOnlyFeedingTools() =
        testApplication {
            setUp()

            val response = rpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val names = response.resultOf(response.bodyAsText())["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
            assertEquals(setOf("get_feeding_log", "record_feeding", "update_feeding_note"), names.toSet())
        }

    @Test
    fun toolCallRunsAsAuthenticatedUser() =
        testApplication {
            setUp()

            val response = rpc(callTool("get_feeding_log", """{"date":"2026-03-14"}"""))

            val result = response.resultOf(response.bodyAsText())
            assertEquals(false, result["isError"]?.jsonPrimitive?.boolean ?: false)
            coVerify { petRepository.getPetsForMember("uid1") }
            coVerify { feedingRepository.getFeedingLog("pet1", "2026-03-14") }
        }

    @Test
    fun invalidToolArgumentsAreReturnedAsToolError() =
        testApplication {
            setUp()

            val response = rpc(callTool("record_feeding", """{"mealTime":"BREAKFAST"}"""))

            val result = response.resultOf(response.bodyAsText())
            assertEquals(true, result["isError"]!!.jsonPrimitive.boolean)
            coVerify(exactly = 0) { feedingRepository.recordFeeding(any(), any(), any(), any()) }
        }

    @Test
    fun getAndDeleteAreNotAllowed() =
        testApplication {
            setUp()

            assertEquals(HttpStatusCode.MethodNotAllowed, client.get(MCP_PATH).status)
            assertEquals(HttpStatusCode.MethodNotAllowed, client.delete(MCP_PATH).status)
        }

    @Test
    fun apiRoutesKeepDefaultJsonSettings() =
        testApplication {
            setUp()

            // 既存 API は従来どおり null を明示的に出力する（McpJson の explicitNulls = false が漏れていない）
            assertEquals("""{"value":null}""", client.get("/api/sample").bodyAsText())
        }
}
