package server.passkey

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.PasskeyAuthenticateCompleteRequest
import model.PasskeyAuthenticateOptionsRequest
import model.PasskeyAuthenticateOptionsResponse
import model.PasskeyAuthenticateResponse
import model.PasskeyRegisterCompleteRequest
import model.PasskeyRegisterOptionsResponse
import model.PasskeyStatusResponse
import server.auth.FirebaseAdmin
import server.auth.authenticated
import server.auth.firebasePrincipal
import java.util.Base64

fun Route.passkeyRoutes() {
    route("/passkey") {
        // 認証済みエンドポイント
        authenticated {
            // パスキー登録状態の確認
            get("/status", {
                tags = listOf("passkey")
                summary = "パスキー登録状態確認"
                response {
                    code(HttpStatusCode.OK) {
                        body<PasskeyStatusResponse>()
                    }
                }
            }) {
                if (!PasskeyService.enabled) {
                    call.respond(PasskeyStatusResponse(registered = true, credentialCount = 0))
                    return@get
                }
                val uid = call.firebasePrincipal.uid
                val registered = PasskeyService.isRegistered(uid)
                val count = if (registered) PasskeyService.credentialCount(uid) else 0
                call.respond(PasskeyStatusResponse(registered = registered, credentialCount = count))
            }

            // 登録オプション生成
            post("/register/options", {
                tags = listOf("passkey")
                summary = "パスキー登録オプション生成"
                response {
                    code(HttpStatusCode.OK) {
                        body<PasskeyRegisterOptionsResponse>()
                    }
                    code(HttpStatusCode.ServiceUnavailable) { description = "パスキー機能無効" }
                }
            }) {
                if (!PasskeyService.enabled) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "パスキー機能が無効です（WEBAUTHN_RP_ID / WEBAUTHN_ORIGIN を設定してください）"),
                    )
                }
                val principal = call.firebasePrincipal
                val uid = principal.uid
                val email = principal.email ?: ""
                val displayName = principal.name ?: email.substringBefore("@")

                val challenge = ChallengeStore.generate(uid)
                val challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)

                val existingCredentials = PasskeyService.findCredentialsByUid(uid)
                val excludeCredentials =
                    existingCredentials.joinToString(",") { cred ->
                        val transportsJson =
                            cred.transports?.split(",")?.joinToString(",") { "\"$it\"" } ?: ""
                        """{"type":"public-key","id":"${cred.credentialIdBase64}"""" +
                            if (transportsJson.isNotEmpty()) ""","transports":[$transportsJson]}""" else "}"
                    }

                val optionsJson =
                    """{
                    |"rp":{"name":"CrabShell","id":"${PasskeyService.rpId}"},
                    |"user":{"id":"${Base64.getUrlEncoder().withoutPadding().encodeToString(
                        uid.toByteArray(),
                    )}","name":"$email","displayName":"$displayName"},
                    |"challenge":"$challengeBase64",
                    |"pubKeyCredParams":[{"type":"public-key","alg":-7},{"type":"public-key","alg":-257}],
                    |"timeout":300000,
                    |"excludeCredentials":[$excludeCredentials],
                    |"authenticatorSelection":{"residentKey":"preferred","userVerification":"preferred"},
                    |"attestation":"none"
                    |}
                    """.trimMargin().replace("\n", "")

                call.respond(PasskeyRegisterOptionsResponse(optionsJson = optionsJson))
            }

            // 登録完了
            post("/register/complete", {
                tags = listOf("passkey")
                summary = "パスキー登録完了"
                request {
                    body<PasskeyRegisterCompleteRequest>()
                }
                response {
                    code(HttpStatusCode.OK) { description = "登録成功" }
                    code(HttpStatusCode.BadRequest) { description = "登録失敗" }
                    code(HttpStatusCode.ServiceUnavailable) { description = "パスキー機能無効" }
                }
            }) {
                if (!PasskeyService.enabled) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "パスキー機能が無効です"),
                    )
                }
                val uid = call.firebasePrincipal.uid
                val request = call.receive<PasskeyRegisterCompleteRequest>()

                val challenge =
                    ChallengeStore.consume(uid)
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "チャレンジが見つからないか期限切れです"),
                        )

                try {
                    val responseJson = Json.parseToJsonElement(request.registrationResponseJSON).jsonObject
                    val response = responseJson["response"]!!.jsonObject

                    val clientDataJSON =
                        Base64.getUrlDecoder().decode(
                            response["clientDataJSON"]!!.jsonPrimitive.content,
                        )
                    val attestationObject =
                        Base64.getUrlDecoder().decode(
                            response["attestationObject"]!!.jsonPrimitive.content,
                        )

                    val transports =
                        response["transports"]
                            ?.jsonArray
                            ?.joinToString(",") { it.jsonPrimitive.content }

                    PasskeyService.verifyAndSaveRegistration(
                        firebaseUid = uid,
                        clientDataJSON = clientDataJSON,
                        attestationObject = attestationObject,
                        challenge = challenge,
                        transports = transports,
                    )

                    call.respond(mapOf("status" to "ok"))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "登録に失敗しました")),
                    )
                }
            }
        }

        // 認証なしエンドポイント
        // 認証オプション生成
        post("/authenticate/options", {
            tags = listOf("passkey")
            summary = "パスキー認証オプション生成"
            securitySchemeNames()
            request {
                body<PasskeyAuthenticateOptionsRequest>()
            }
            response {
                code(HttpStatusCode.OK) {
                    body<PasskeyAuthenticateOptionsResponse>()
                }
                code(HttpStatusCode.BadRequest) { description = "ユーザー未発見またはパスキー未登録" }
                code(HttpStatusCode.ServiceUnavailable) { description = "パスキー機能無効" }
            }
        }) {
            if (!PasskeyService.enabled) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to "パスキー機能が無効です"),
                )
            }
            val request = call.receive<PasskeyAuthenticateOptionsRequest>()

            val user =
                FirebaseAdmin.getUserByEmail(request.email)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "ユーザーが見つかりません"),
                    )

            val credentials = PasskeyService.findCredentialsByUid(user.uid)
            if (credentials.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "パスキーが登録されていません"),
                )
            }

            val challenge = ChallengeStore.generate(request.email)
            val challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)

            val allowCredentials =
                credentials.joinToString(",") { cred ->
                    val transportsJson =
                        cred.transports?.split(",")?.joinToString(",") { "\"$it\"" } ?: ""
                    """{"type":"public-key","id":"${cred.credentialIdBase64}"""" +
                        if (transportsJson.isNotEmpty()) ""","transports":[$transportsJson]}""" else "}"
                }

            val optionsJson =
                """{
                |"challenge":"$challengeBase64",
                |"timeout":300000,
                |"rpId":"${PasskeyService.rpId}",
                |"allowCredentials":[$allowCredentials],
                |"userVerification":"preferred"
                |}
                """.trimMargin().replace("\n", "")

            call.respond(PasskeyAuthenticateOptionsResponse(optionsJson = optionsJson))
        }

        // 認証完了
        post("/authenticate/complete", {
            tags = listOf("passkey")
            summary = "パスキー認証完了"
            securitySchemeNames()
            request {
                body<PasskeyAuthenticateCompleteRequest>()
            }
            response {
                code(HttpStatusCode.OK) {
                    body<PasskeyAuthenticateResponse>()
                }
                code(HttpStatusCode.BadRequest) { description = "認証失敗" }
                code(HttpStatusCode.ServiceUnavailable) { description = "パスキー機能無効" }
            }
        }) {
            if (!PasskeyService.enabled) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to "パスキー機能が無効です"),
                )
            }
            val request = call.receive<PasskeyAuthenticateCompleteRequest>()

            val challenge =
                ChallengeStore.consume(request.email)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "チャレンジが見つからないか期限切れです"),
                    )

            try {
                val responseJson =
                    Json.parseToJsonElement(request.authenticationResponseJSON).jsonObject
                val credentialIdBase64 = responseJson["id"]!!.jsonPrimitive.content
                val response = responseJson["response"]!!.jsonObject

                val clientDataJSON =
                    Base64.getUrlDecoder().decode(
                        response["clientDataJSON"]!!.jsonPrimitive.content,
                    )
                val authenticatorDataBytes =
                    Base64.getUrlDecoder().decode(
                        response["authenticatorData"]!!.jsonPrimitive.content,
                    )
                val signature =
                    Base64.getUrlDecoder().decode(
                        response["signature"]!!.jsonPrimitive.content,
                    )
                val credentialIdBytes = Base64.getUrlDecoder().decode(credentialIdBase64)

                val credentialRecord =
                    PasskeyService.findCredentialByCredentialId(credentialIdBase64)
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "クレデンシャルが見つかりません"),
                        )

                PasskeyService.verifyAuthentication(
                    credentialIdBytes = credentialIdBytes,
                    clientDataJSON = clientDataJSON,
                    authenticatorData = authenticatorDataBytes,
                    signature = signature,
                    challenge = challenge,
                    credentialRecord = credentialRecord,
                )

                val customToken =
                    FirebaseAdmin.createCustomToken(credentialRecord.firebaseUid)
                        ?: return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Custom Token の生成に失敗しました"),
                        )

                call.respond(PasskeyAuthenticateResponse(customToken = customToken))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "認証に失敗しました")),
                )
            }
        }
    }
}
