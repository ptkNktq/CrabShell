package server.passkey

import org.jetbrains.exposed.v1.core.Table

object PasskeyCredentials : Table("passkey_credentials") {
    val id = long("id").autoIncrement()
    val firebaseUid = varchar("firebase_uid", 128).index()
    val credentialId = binary("credential_id")
    val credentialIdBase64 = varchar("credential_id_base64", 512).index()
    val publicKey = binary("public_key")
    val counter = long("counter").default(0)
    val transports = varchar("transports", 256).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
