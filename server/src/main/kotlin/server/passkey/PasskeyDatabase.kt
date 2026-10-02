package server.passkey

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * パスキー用 SQLite DB への接続。生成時に接続し、テーブルが無ければ作成する。
 *
 * Exposed の [Database] は汎用型のため、DI ではこの専用の型で受け渡して他の DB と取り違えないようにする。
 */
class PasskeyDatabase(
    dbPath: String,
) : AutoCloseable {
    val database: Database

    private val closed = AtomicBoolean(false)

    init {
        File(dbPath).parentFile?.mkdirs()
        database = Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        transaction(database) {
            SchemaUtils.create(PasskeyCredentials)
        }
    }

    /**
     * Exposed の TransactionManager から登録を解除し、以降のトランザクションで使えなくする。
     * URL 指定の接続はトランザクションごとに開閉するため、常駐するコネクションは持たない。
     * 2 回目以降の呼び出しは何もしない。
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            TransactionManager.closeAndUnregister(database)
        }
    }
}
