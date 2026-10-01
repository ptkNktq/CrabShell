package server.passkey

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed（SQLite）による [PasskeyCredentialRepository] の実装 */
class ExposedPasskeyCredentialRepository(
    private val database: Database,
) : PasskeyCredentialRepository {
    override fun countByUid(firebaseUid: String): Int =
        transaction(database) {
            PasskeyCredentials
                .selectAll()
                .where { PasskeyCredentials.firebaseUid eq firebaseUid }
                .count()
                .toInt()
        }

    override fun findByUid(firebaseUid: String): List<PasskeyCredentialRecord> =
        transaction(database) {
            PasskeyCredentials
                .selectAll()
                .where { PasskeyCredentials.firebaseUid eq firebaseUid }
                .orderBy(PasskeyCredentials.createdAt)
                .map { it.toRecord() }
        }

    override fun findByCredentialId(credentialIdBase64: String): PasskeyCredentialRecord? =
        transaction(database) {
            PasskeyCredentials
                .selectAll()
                .where { PasskeyCredentials.credentialIdBase64 eq credentialIdBase64 }
                .firstOrNull()
                ?.toRecord()
        }

    override fun save(
        firebaseUid: String,
        credential: RegisteredCredential,
        transports: String?,
    ) {
        transaction(database) {
            PasskeyCredentials.insert {
                it[PasskeyCredentials.firebaseUid] = firebaseUid
                it[PasskeyCredentials.credentialId] = credential.credentialId
                it[PasskeyCredentials.credentialIdBase64] = credential.credentialIdBase64
                it[PasskeyCredentials.publicKey] = credential.publicKey
                it[PasskeyCredentials.counter] = credential.counter
                it[PasskeyCredentials.transports] = transports
                it[PasskeyCredentials.createdAt] = System.currentTimeMillis()
            }
        }
    }

    override fun updateCounter(
        id: Long,
        counter: Long,
    ) {
        transaction(database) {
            PasskeyCredentials.update({ PasskeyCredentials.id eq id }) {
                it[PasskeyCredentials.counter] = counter
            }
        }
    }

    override fun deleteByUid(firebaseUid: String): Int =
        transaction(database) {
            PasskeyCredentials.deleteWhere { PasskeyCredentials.firebaseUid eq firebaseUid }
        }

    override fun deleteByIdAndUid(
        id: Long,
        firebaseUid: String,
    ): Boolean =
        transaction(database) {
            PasskeyCredentials.deleteWhere {
                (PasskeyCredentials.id eq id) and (PasskeyCredentials.firebaseUid eq firebaseUid)
            }
        } > 0

    private fun ResultRow.toRecord(): PasskeyCredentialRecord =
        PasskeyCredentialRecord(
            id = this[PasskeyCredentials.id],
            firebaseUid = this[PasskeyCredentials.firebaseUid],
            credentialId = this[PasskeyCredentials.credentialId],
            credentialIdBase64 = this[PasskeyCredentials.credentialIdBase64],
            publicKey = this[PasskeyCredentials.publicKey],
            counter = this[PasskeyCredentials.counter],
            transports = this[PasskeyCredentials.transports],
            createdAt = this[PasskeyCredentials.createdAt],
        )
}
