package server.passkey

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import server.config.EnvConfig
import java.io.File

object PasskeyDatabase {
    fun initialize() {
        val dbPath = EnvConfig["PASSKEY_DB_PATH"] ?: "data/passkey.db"
        File(dbPath).parentFile?.mkdirs()

        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(PasskeyCredentials)
        }
    }
}
