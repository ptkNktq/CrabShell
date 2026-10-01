package server.passkey

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * WebAuthn の登録・認証レスポンスを検証する（webauthn4j のラッパー）。
 * DB には触れず、検証結果を返すだけにする（保存は [PasskeyCredentialRepository] の責務）。
 */
class WebAuthnVerifier(
    private val config: PasskeyConfig,
) {
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(ObjectConverter())

    /**
     * clientDataJSON からチャレンジを抽出する。
     * usernameless 認証ではユーザー識別子が事前にわからないため、
     * [ChallengeStore.consumeAnonymous] の検証対象を得るために呼び出す。
     */
    fun extractChallenge(clientDataJSON: ByteArray): ByteArray {
        val challengeStr =
            parseClientData(clientDataJSON)["challenge"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("clientDataJSON に challenge がありません")
        return Base64.getUrlDecoder().decode(challengeStr)
    }

    /** 登録レスポンスを検証し、保存すべきパスキーの情報を返す。検証に失敗した場合は例外を投げる */
    fun verifyRegistration(
        clientDataJSON: ByteArray,
        attestationObject: ByteArray,
        challenge: ByteArray,
    ): RegisteredCredential {
        val registrationRequest = RegistrationRequest(attestationObject, clientDataJSON)
        val registrationParameters =
            RegistrationParameters(serverProperty(clientDataJSON, challenge), null, false, true)

        val data = webAuthnManager.validate(registrationRequest, registrationParameters)
        val authenticatorData = data.attestationObject!!.authenticatorData
        val attestedCredentialData = authenticatorData.attestedCredentialData!!
        val credentialId = attestedCredentialData.credentialId
        return RegisteredCredential(
            credentialId = credentialId,
            credentialIdBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId),
            publicKey = attestedCredentialDataConverter.convert(attestedCredentialData),
            counter = authenticatorData.signCount,
        )
    }

    /** 認証レスポンスを検証し、更新後の署名カウンタを返す。検証に失敗した場合は例外を投げる */
    fun verifyAuthentication(
        credentialIdBytes: ByteArray,
        clientDataJSON: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        challenge: ByteArray,
        credentialRecord: PasskeyCredentialRecord,
    ): Long {
        val authenticator =
            com.webauthn4j.authenticator.AuthenticatorImpl(
                attestedCredentialDataConverter.convert(credentialRecord.publicKey),
                null,
                credentialRecord.counter,
            )

        val authenticationRequest =
            AuthenticationRequest(
                credentialIdBytes,
                authenticatorData,
                clientDataJSON,
                signature,
            )

        val authenticationParameters =
            AuthenticationParameters(
                serverProperty(clientDataJSON, challenge),
                authenticator,
                null,
                false,
                true,
            )

        val data = webAuthnManager.validate(authenticationRequest, authenticationParameters)
        return data.authenticatorData!!.signCount
    }

    private fun serverProperty(
        clientDataJSON: ByteArray,
        challenge: ByteArray,
    ): ServerProperty = ServerProperty(resolveOrigin(clientDataJSON), config.rpId, DefaultChallenge(challenge), null)

    /** clientDataJSON からオリジンを抽出し、許可リストと照合して返す */
    private fun resolveOrigin(clientDataJSON: ByteArray): Origin {
        val originStr =
            parseClientData(clientDataJSON)["origin"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("clientDataJSON に origin がありません")
        if (originStr !in config.allowedOrigins) {
            throw IllegalArgumentException(
                "origin '$originStr' は許可されていません (許可: ${config.allowedOrigins})",
            )
        }
        return Origin.create(originStr)
    }

    private fun parseClientData(clientDataJSON: ByteArray) = Json.parseToJsonElement(String(clientDataJSON, Charsets.UTF_8)).jsonObject
}
