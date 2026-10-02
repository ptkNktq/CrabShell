package server.passkey

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import server.util.CloseOnce
import java.io.File

/**
 * パスキー用 SQLite DB への接続。生成時に接続し、テーブルが無ければ作成する。
 *
 * Exposed の [Database] は汎用型のため、DI ではこの専用の型で受け渡して他の DB と取り違えないようにする。
 */
class PasskeyDatabase(
    dbPath: String,
) : AutoCloseable {
    val database: Database

    private val closeOnce = CloseOnce()

    init {
        File(dbPath).parentFile?.mkdirs()
        database = Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        try {
            transaction(database) {
                SchemaUtils.create(PasskeyCredentials)
            }
        } catch (e: Exception) {
            // 生成に失敗するとインスタンスが返らず close() されないため、ここで登録を解除してから投げ直す
            TransactionManager.closeAndUnregister(database)
            throw e
        }
    }

    /**
     * Exposed の TransactionManager から登録を解除し、以降のトランザクションで使えなくする。
     * URL 指定の接続はトランザクションごとに開閉するため、常駐するコネクションは持たない。
     * 2 回目以降の呼び出しは何もしない。
     */
    override fun close() = closeOnce { TransactionManager.closeAndUnregister(database) }
}
