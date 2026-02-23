package server.passkey

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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

                val optionsJson =
                    Json.encodeToString(
                        buildJsonObject {
                            putJsonObject("rp") {
                                put("name", "CrabShell")
                                put("id", PasskeyService.rpId)
                            }
                            putJsonObject("user") {
                                put(
                                    "id",
                                    Base64.getUrlEncoder().withoutPadding().encodeToString(uid.toByteArray()),
                                )
                                put("name", email)
                                put("displayName", displayName)
                            }
                            put("challenge", challengeBase64)
                            putJsonArray("pubKeyCredParams") {
                                add(
                                    buildJsonObject {
                                        put("type", "public-key")
                                        put("alg", -7)
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", "public-key")
                                        put("alg", -257)
                                    },
                                )
                            }
                            put("timeout", 300000)
                            putJsonArray("excludeCredentials") {
                                existingCredentials.forEach { cred ->
                                    add(buildCredentialJsonObject(cred))
                                }
                            }
                            putJsonObject("authenticatorSelection") {
                                put("residentKey", "preferred")
                                put("userVerification", "preferred")
                            }
                            put("attestation", "none")
                        },
                    )

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
                code(HttpStatusCode.BadRequest) { description = "認証不可" }
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

            val user = FirebaseAdmin.getUserByEmail(request.email)
            val credentials = user?.let { PasskeyService.findCredentialsByUid(it.uid) } ?: emptyList()
            if (credentials.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "認証できません"),
                )
            }

            val challenge = ChallengeStore.generate(request.email)
            val challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)

            val optionsJson =
                Json.encodeToString(
                    buildJsonObject {
                        put("challenge", challengeBase64)
                        put("timeout", 300000)
                        put("rpId", PasskeyService.rpId)
                        putJsonArray("allowCredentials") {
                            credentials.forEach { cred ->
                                add(buildCredentialJsonObject(cred))
                            }
                        }
                        put("userVerification", "preferred")
                    },
                )

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

            val authError = mapOf("error" to "認証に失敗しました")

            val challenge =
                ChallengeStore.consume(request.email)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, authError)

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
                        ?: return@post call.respond(HttpStatusCode.BadRequest, authError)

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
                            mapOf("error" to "認証処理に失敗しました"),
                        )

                call.respond(PasskeyAuthenticateResponse(customToken = customToken))
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, authError)
            }
        }
    }
}

/** クレデンシャル情報を WebAuthn 仕様の JSON オブジェクトに変換する */
private fun buildCredentialJsonObject(cred: PasskeyService.CredentialRecord) =
    buildJsonObject {
        put("type", "public-key")
        put("id", cred.credentialIdBase64)
        cred.transports?.split(",")?.let { transports ->
            putJsonArray("transports") {
                transports.forEach { add(JsonPrimitive(it)) }
            }
        }
    }
