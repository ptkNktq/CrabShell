package model

import kotlinx.serialization.Serializable

@Serializable
data class PasskeyStatusResponse(
    val registered: Boolean,
    val credentialCount: Int = 0,
)

@Serializable
data class PasskeyRegisterOptionsResponse(
    val optionsJson: String,
)

@Serializable
data class PasskeyRegisterCompleteRequest(
    val registrationResponseJSON: String,
)

@Serializable
data class PasskeyAuthenticateOptionsResponse(
    val optionsJson: String,
)

@Serializable
data class PasskeyAuthenticateCompleteRequest(
    val authenticationResponseJSON: String,
)

@Serializable
data class PasskeyAuthenticateResponse(
    val customToken: String,
)

@Serializable
data class PasskeyCredentialInfo(
    val id: Long,
    val createdAt: String,
    val transports: List<String> = emptyList(),
)

@Serializable
data class PasskeyCredentialsResponse(
    val credentials: List<PasskeyCredentialInfo>,
)
