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
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerializationException
import server.pet.PetAccessDeniedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusPagesTest {
    @Test
    fun exceptionsAreConvertedToJsonErrorResponses() =
        testApplication {
            application {
                configureStatusPages()
                // 本番と同じく ContentNegotiation はルーティングのルートに入れる
                routing {
                    install(ContentNegotiation) { json() }
                    get("/denied") { throw PetAccessDeniedException("pet1", "uid1") }
                    get("/missing") { throw MissingRequestParameterException("date") }
                    get("/conversion") { throw ParameterConversionException("mealTime", "MealTime") }
                    get("/bad") { throw BadRequestException("bad") }
                    get("/serialization") { throw SerializationException("internal detail") }
                }
            }

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
}
