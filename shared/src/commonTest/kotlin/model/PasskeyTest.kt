package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PasskeyTest {
    private val json = Json

    @Test
    fun passkeyStatusResponseRoundTrip() {
        val status = PasskeyStatusResponse(registered = true, credentialCount = 2)
        val encoded = json.encodeToString(PasskeyStatusResponse.serializer(), status)
        val decoded = json.decodeFromString(PasskeyStatusResponse.serializer(), encoded)
        assertEquals(status, decoded)
    }

    @Test
    fun passkeyStatusResponseDefault() {
        val jsonStr = """{"registered":false}"""
        val decoded = json.decodeFromString(PasskeyStatusResponse.serializer(), jsonStr)
        assertEquals(false, decoded.registered)
        assertEquals(0, decoded.credentialCount)
    }

    @Test
    fun passkeyRegisterModelsRoundTrip() {
        val opts = PasskeyRegisterOptionsResponse(optionsJson = """{"rp":"example"}""")
        val req = PasskeyRegisterCompleteRequest(registrationResponseJSON = """{"id":"abc"}""")
        assertEquals(
            opts,
            json.decodeFromString(
                PasskeyRegisterOptionsResponse.serializer(),
                json.encodeToString(PasskeyRegisterOptionsResponse.serializer(), opts),
            ),
        )
        assertEquals(
            req,
            json.decodeFromString(
                PasskeyRegisterCompleteRequest.serializer(),
                json.encodeToString(PasskeyRegisterCompleteRequest.serializer(), req),
            ),
        )
    }

    @Test
    fun passkeyAuthenticateModelsRoundTrip() {
        val optRes = PasskeyAuthenticateOptionsResponse(optionsJson = """{"challenge":"x"}""")
        val completeReq = PasskeyAuthenticateCompleteRequest(authenticationResponseJSON = """{"id":"y"}""")
        val authRes = PasskeyAuthenticateResponse(customToken = "token123")

        assertEquals(
            optRes,
            json.decodeFromString(
                PasskeyAuthenticateOptionsResponse.serializer(),
                json.encodeToString(PasskeyAuthenticateOptionsResponse.serializer(), optRes),
            ),
        )
        assertEquals(
            completeReq,
            json.decodeFromString(
                PasskeyAuthenticateCompleteRequest.serializer(),
                json.encodeToString(PasskeyAuthenticateCompleteRequest.serializer(), completeReq),
            ),
        )
        assertEquals(
            authRes,
            json.decodeFromString(
                PasskeyAuthenticateResponse.serializer(),
                json.encodeToString(PasskeyAuthenticateResponse.serializer(), authRes),
            ),
        )
    }

    @Test
    fun passkeyCredentialsResponseRoundTrip() {
        val response =
            PasskeyCredentialsResponse(
                credentials =
                    listOf(
                        PasskeyCredentialInfo(id = 1, createdAt = "2026-01-01T00:00:00Z", transports = listOf("internal")),
                        PasskeyCredentialInfo(id = 2, createdAt = "2026-02-01T00:00:00Z"),
                    ),
            )
        val encoded = json.encodeToString(PasskeyCredentialsResponse.serializer(), response)
        val decoded = json.decodeFromString(PasskeyCredentialsResponse.serializer(), encoded)
        assertEquals(response, decoded)
        assertEquals(emptyList(), decoded.credentials[1].transports)
    }
}
