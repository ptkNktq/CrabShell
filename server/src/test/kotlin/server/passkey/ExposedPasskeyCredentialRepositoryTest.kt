package server.passkey

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedPasskeyCredentialRepositoryTest {
    // テストごとに一時ファイルの SQLite DB を使い、テスト間でデータを共有しない
    private val dbDir: File = Files.createTempDirectory("passkey-test").toFile()
    private val passkeyDatabase = PasskeyDatabase(File(dbDir, "passkey.db").path)
    private val repository = ExposedPasskeyCredentialRepository(passkeyDatabase)

    @AfterTest
    fun tearDown() {
        // Exposed はグローバルな TransactionManager に接続を登録するため、テストごとに解除する
        passkeyDatabase.close()
        dbDir.deleteRecursively()
    }

    private fun credential(idByte: Int): RegisteredCredential =
        RegisteredCredential(
            credentialId = byteArrayOf(idByte.toByte()),
            credentialIdBase64 = "cred-$idByte",
            publicKey = byteArrayOf(9, 9),
            counter = 0,
        )

    @Test
    fun saveAndFindByCredentialId() {
        repository.save("user1", credential(1), "internal,hybrid")

        val found = assertNotNull(repository.findByCredentialId("cred-1"))
        assertEquals("user1", found.firebaseUid)
        assertEquals(listOf<Byte>(1), found.credentialId.toList())
        assertEquals("internal,hybrid", found.transports)
        assertNull(repository.findByCredentialId("cred-unknown"))
    }

    @Test
    fun countAndFindByUidOnlyReturnOwnCredentials() {
        repository.save("user1", credential(1), null)
        repository.save("user1", credential(2), null)
        repository.save("user2", credential(3), null)

        assertEquals(2, repository.countByUid("user1"))
        assertEquals(listOf("cred-1", "cred-2"), repository.findByUid("user1").map { it.credentialIdBase64 })
        assertEquals(0, repository.countByUid("nobody"))
    }

    @Test
    fun updateCounterChangesOnlyTargetCredential() {
        repository.save("user1", credential(1), null)
        repository.save("user1", credential(2), null)
        val target = assertNotNull(repository.findByCredentialId("cred-1"))

        repository.updateCounter(target.id, 42)

        assertEquals(42, repository.findByCredentialId("cred-1")?.counter)
        assertEquals(0, repository.findByCredentialId("cred-2")?.counter)
    }

    @Test
    fun deleteByUidDeletesAllOfUserAndReturnsCount() {
        repository.save("user1", credential(1), null)
        repository.save("user1", credential(2), null)
        repository.save("user2", credential(3), null)

        assertEquals(2, repository.deleteByUid("user1"))
        assertEquals(0, repository.countByUid("user1"))
        assertEquals(1, repository.countByUid("user2"))
    }

    @Test
    fun deleteByIdAndUidDoesNotDeleteOtherUsersCredential() {
        repository.save("user2", credential(3), null)
        val othersCredential = assertNotNull(repository.findByCredentialId("cred-3"))

        assertFalse(repository.deleteByIdAndUid(othersCredential.id, "user1"))
        assertEquals(1, repository.countByUid("user2"))

        assertTrue(repository.deleteByIdAndUid(othersCredential.id, "user2"))
        assertEquals(0, repository.countByUid("user2"))
    }
}
