package server

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerializationException
import server.pet.PetAccessDeniedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StatusPagesTest {
    @Test
    fun exceptionsAreConvertedToJsonErrorResponses() =
        testApplication {
            application {
                configureStatusPages()
                install(RateLimit) {
                    register(LIMITED) { rateLimiter(limit = 1, refillPeriod = 60.seconds) }
                }
                // 本番と同じく ContentNegotiation はルーティングのルートに入れる
                routing {
                    install(ContentNegotiation) { json() }
                    rateLimit(LIMITED) { get("/limited") { call.respondText("ok") } }
                    get("/denied") { throw PetAccessDeniedException("pet1", "uid1") }
                    get("/missing") { throw MissingRequestParameterException("date") }
                    get("/conversion") { throw ParameterConversionException("mealTime", "MealTime") }
                    get("/bad") { throw BadRequestException("bad") }
                    get("/serialization") { throw SerializationException("internal detail") }
                }
            }

            client.get("/limited")
            client.get("/limited").assertError(HttpStatusCode.TooManyRequests, "Too many requests")
            client.get("/denied").assertError(HttpStatusCode.Forbidden, "Not a member of this pet")
            client.get("/missing").assertError(HttpStatusCode.BadRequest, "date is required")
            client.get("/conversion").assertError(HttpStatusCode.BadRequest, "Invalid mealTime: MealTime")
            client.get("/bad").assertError(HttpStatusCode.BadRequest, "bad")
            client.get("/serialization").assertError(HttpStatusCode.BadRequest, "Invalid request body")
        }

    private suspend fun HttpResponse.assertError(
        status: HttpStatusCode,
        message: String,
    ) {
        assertEquals(status, this.status)
        assertTrue(contentType()?.match(ContentType.Application.Json) == true)
        assertEquals("""{"error":"$message"}""", bodyAsText())
    }

    companion object {
        private val LIMITED = RateLimitName("limited")
    }
}
