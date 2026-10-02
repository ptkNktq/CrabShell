package server.passkey

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PasskeyDatabaseTest {
    private val dir: File = Files.createTempDirectory("passkey-db-test").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun unregistersDatabaseWhenCreationFails() {
        // 親ディレクトリの位置にファイルを置き、DB ファイルを作れない（テーブル作成で失敗する）状態にする
        val notADirectory = File(dir, "not-a-directory").apply { writeText("") }
        val primaryBefore = TransactionManager.primaryDatabase

        assertFailsWith<Exception> { PasskeyDatabase(File(notADirectory, "passkey.db").path) }

        // primaryDatabase は既定 DB が未設定なら最後に登録した DB を返すため、
        // 登録が残っていれば失敗した DB になる。解除されていれば失敗前と変わらない
        assertSame(primaryBefore, TransactionManager.primaryDatabase)
    }
}
