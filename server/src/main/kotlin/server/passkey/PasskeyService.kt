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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.Base64

object PasskeyService {
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val objectConverter = ObjectConverter()
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    val rpId: String by lazy {
        System.getenv("WEBAUTHN_RP_ID") ?: "localhost"
    }

    val allowedOrigins: Set<String> by lazy {
        (System.getenv("WEBAUTHN_ORIGIN") ?: "http://localhost:8080,http://localhost:3000")
            .split(",")
            .map { it.trim() }
            .toSet()
    }

    /** clientDataJSON からオリジンを抽出し、許可リストと照合して返す */
    private fun resolveOrigin(clientDataJSON: ByteArray): Origin {
        val json = String(clientDataJSON, Charsets.UTF_8)
        val parsed = Json.parseToJsonElement(json).jsonObject
        val originStr =
            parsed["origin"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("clientDataJSON に origin がありません")
        if (originStr !in allowedOrigins) {
            throw IllegalArgumentException(
                "origin '$originStr' は許可されていません (許可: $allowedOrigins)",
            )
        }
        return Origin.create(originStr)
    }

    fun isRegistered(firebaseUid: String): Boolean =
        transaction {
            PasskeyCredentials.selectAll()
                .where { PasskeyCredentials.firebaseUid eq firebaseUid }
                .count() > 0
        }

    fun credentialCount(firebaseUid: String): Int =
        transaction {
            PasskeyCredentials.selectAll()
                .where { PasskeyCredentials.firebaseUid eq firebaseUid }
                .count().toInt()
        }

    fun verifyAndSaveRegistration(
        firebaseUid: String,
        clientDataJSON: ByteArray,
        attestationObject: ByteArray,
        challenge: ByteArray,
        transports: String?,
    ) {
        val serverProperty =
            ServerProperty(
                resolveOrigin(clientDataJSON),
                rpId,
                DefaultChallenge(challenge),
                null,
            )

        val registrationRequest = RegistrationRequest(attestationObject, clientDataJSON)
        val registrationParameters = RegistrationParameters(serverProperty, null, false, true)

        val data = webAuthnManager.validate(registrationRequest, registrationParameters)
        val attestedCredentialData = data.attestationObject!!.authenticatorData.attestedCredentialData!!
        val credentialIdBytes = attestedCredentialData.credentialId
        val credentialIdBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialIdBytes)
        val publicKeyBytes = attestedCredentialDataConverter.convert(attestedCredentialData)
        val counter = data.attestationObject!!.authenticatorData.signCount

        transaction {
            PasskeyCredentials.insert {
                it[PasskeyCredentials.firebaseUid] = firebaseUid
                it[PasskeyCredentials.credentialId] = credentialIdBytes
                it[PasskeyCredentials.credentialIdBase64] = credentialIdBase64
                it[PasskeyCredentials.publicKey] = publicKeyBytes
                it[PasskeyCredentials.counter] = counter
                it[PasskeyCredentials.transports] = transports
                it[PasskeyCredentials.createdAt] = System.currentTimeMillis()
            }
        }
    }

    data class CredentialRecord(
        val id: Long,
        val firebaseUid: String,
        val credentialId: ByteArray,
        val credentialIdBase64: String,
        val publicKey: ByteArray,
        val counter: Long,
        val transports: String?,
    )

    fun findCredentialsByUid(firebaseUid: String): List<CredentialRecord> =
        transaction {
            PasskeyCredentials.selectAll()
                .where { PasskeyCredentials.firebaseUid eq firebaseUid }
                .map {
                    CredentialRecord(
                        id = it[PasskeyCredentials.id],
                        firebaseUid = it[PasskeyCredentials.firebaseUid],
                        credentialId = it[PasskeyCredentials.credentialId],
                        credentialIdBase64 = it[PasskeyCredentials.credentialIdBase64],
                        publicKey = it[PasskeyCredentials.publicKey],
                        counter = it[PasskeyCredentials.counter],
                        transports = it[PasskeyCredentials.transports],
                    )
                }
        }

    fun findCredentialByCredentialId(credentialIdBase64: String): CredentialRecord? =
        transaction {
            PasskeyCredentials.selectAll()
                .where { PasskeyCredentials.credentialIdBase64 eq credentialIdBase64 }
                .firstOrNull()
                ?.let {
                    CredentialRecord(
                        id = it[PasskeyCredentials.id],
                        firebaseUid = it[PasskeyCredentials.firebaseUid],
                        credentialId = it[PasskeyCredentials.credentialId],
                        credentialIdBase64 = it[PasskeyCredentials.credentialIdBase64],
                        publicKey = it[PasskeyCredentials.publicKey],
                        counter = it[PasskeyCredentials.counter],
                        transports = it[PasskeyCredentials.transports],
                    )
                }
        }

    fun verifyAuthentication(
        credentialIdBytes: ByteArray,
        clientDataJSON: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        challenge: ByteArray,
        credentialRecord: CredentialRecord,
    ): Long {
        val serverProperty =
            ServerProperty(
                resolveOrigin(clientDataJSON),
                rpId,
                DefaultChallenge(challenge),
                null,
            )

        val attestedCredentialData =
            attestedCredentialDataConverter.convert(credentialRecord.publicKey)

        val authenticator =
            com.webauthn4j.authenticator.AuthenticatorImpl(
                attestedCredentialData,
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
                serverProperty,
                authenticator,
                null,
                false,
                true,
            )

        val data = webAuthnManager.validate(authenticationRequest, authenticationParameters)
        val newCounter = data.authenticatorData!!.signCount

        // カウンターを更新
        transaction {
            PasskeyCredentials.update({ PasskeyCredentials.id eq credentialRecord.id }) {
                it[counter] = newCounter
            }
        }

        return newCounter
    }

    fun deleteCredentials(firebaseUid: String) {
        transaction {
            PasskeyCredentials.deleteWhere { PasskeyCredentials.firebaseUid eq firebaseUid }
        }
    }
}
