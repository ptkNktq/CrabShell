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
        val optReq = PasskeyAuthenticateOptionsRequest(email = "a@b.com")
        val optRes = PasskeyAuthenticateOptionsResponse(optionsJson = """{"challenge":"x"}""")
        val completeReq = PasskeyAuthenticateCompleteRequest(email = "a@b.com", authenticationResponseJSON = """{"id":"y"}""")
        val authRes = PasskeyAuthenticateResponse(customToken = "token123")

        assertEquals(
            optReq,
            json.decodeFromString(
                PasskeyAuthenticateOptionsRequest.serializer(),
                json.encodeToString(PasskeyAuthenticateOptionsRequest.serializer(), optReq),
            ),
        )
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
}
