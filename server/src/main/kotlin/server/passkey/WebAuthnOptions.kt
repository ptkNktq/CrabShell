package server.passkey

import kotlinx.serialization.Serializable

/** WebAuthn PublicKeyCredentialCreationOptions（登録用） */
@Serializable
data class CreationOptions(
    val rp: RelyingParty,
    val user: UserEntity,
    val challenge: String,
    val pubKeyCredParams: List<PubKeyCredParam>,
    val timeout: Int = 300000,
    val excludeCredentials: List<CredentialDescriptor> = emptyList(),
    val authenticatorSelection: AuthenticatorSelection = AuthenticatorSelection(),
    val attestation: String = "none",
)

/** WebAuthn PublicKeyCredentialRequestOptions（認証用） */
@Serializable
data class RequestOptions(
    val challenge: String,
    val timeout: Int = 300000,
    val rpId: String,
    val allowCredentials: List<CredentialDescriptor> = emptyList(),
    val userVerification: String = "preferred",
)

@Serializable
data class RelyingParty(
    val name: String,
    val id: String,
)

@Serializable
data class UserEntity(
    val id: String,
    val name: String,
    val displayName: String,
)

@Serializable
data class PubKeyCredParam(
    val type: String = "public-key",
    val alg: Int,
)

@Serializable
data class CredentialDescriptor(
    val type: String = "public-key",
    val id: String,
    val transports: List<String>? = null,
)

@Serializable
data class AuthenticatorSelection(
    val residentKey: String = "preferred",
    val userVerification: String = "preferred",
)
