package core.network

import core.auth.toJsString
import core.auth.webAuthnCreateCredential
import core.auth.webAuthnGetCredential
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.await
import model.PasskeyAuthenticateCompleteRequest
import model.PasskeyAuthenticateOptionsRequest
import model.PasskeyAuthenticateOptionsResponse
import model.PasskeyAuthenticateResponse
import model.PasskeyRegisterCompleteRequest
import model.PasskeyRegisterOptionsResponse
import model.PasskeyStatusResponse

class PasskeyRepositoryImpl(
    private val httpClient: HttpClient,
) : PasskeyRepository {
    override suspend fun getPasskeyStatus(): Result<PasskeyStatusResponse> =
        runCatching {
            httpClient.get("/api/passkey/status").body<PasskeyStatusResponse>()
        }

    override suspend fun registerPasskey(displayName: String?): Result<Unit> =
        runCatching {
            // 1. サーバーからオプション取得
            val optionsResponse =
                httpClient
                    .post("/api/passkey/register/options") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("displayName" to displayName))
                    }.body<PasskeyRegisterOptionsResponse>()

            // 2. WebAuthn API 呼出
            val credentialJson =
                webAuthnCreateCredential(optionsResponse.optionsJson.toJsString())
                    .await<JsString>()
                    .toString()

            // 3. サーバーに結果送信
            httpClient.post("/api/passkey/register/complete") {
                contentType(ContentType.Application.Json)
                setBody(PasskeyRegisterCompleteRequest(registrationResponseJSON = credentialJson))
            }
        }

    override suspend fun authenticateWithPasskey(email: String): Result<String> =
        runCatching {
            // 認証なし HttpClient を使用
            val unauthClient = createUnauthenticatedClient()
            try {
                // 1. サーバーからオプション取得
                val optionsResponse =
                    unauthClient
                        .post("/api/passkey/authenticate/options") {
                            contentType(ContentType.Application.Json)
                            setBody(PasskeyAuthenticateOptionsRequest(email = email))
                        }.body<PasskeyAuthenticateOptionsResponse>()

                // 2. WebAuthn API 呼出
                val credentialJson =
                    webAuthnGetCredential(optionsResponse.optionsJson.toJsString())
                        .await<JsString>()
                        .toString()

                // 3. サーバーに結果送信
                val response =
                    unauthClient
                        .post("/api/passkey/authenticate/complete") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                PasskeyAuthenticateCompleteRequest(
                                    email = email,
                                    authenticationResponseJSON = credentialJson,
                                ),
                            )
                        }.body<PasskeyAuthenticateResponse>()

                response.customToken
            } finally {
                unauthClient.close()
            }
        }
}
