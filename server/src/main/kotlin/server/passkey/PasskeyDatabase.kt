package server.passkey

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

/** パスキー用 SQLite DB に接続し、テーブルが無ければ作成して返す */
fun connectPasskeyDatabase(dbPath: String): Database {
    File(dbPath).parentFile?.mkdirs()
    val database = Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
    transaction(database) {
        SchemaUtils.create(PasskeyCredentials)
    }
    return database
}
