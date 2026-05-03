package server.loginhistory

import model.LoginEvent
import model.LoginMethod
import java.time.Instant

/** ログイン履歴の書き込み用コマンド。読み取り用 [LoginEvent] と責務を分離する。 */
data class RecordLoginInput(
    val docId: String,
    val timestamp: Instant,
    val expireAt: Instant,
    val ipAddress: String?,
    val userAgent: String?,
    val loginMethod: LoginMethod?,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
)

interface LoginHistoryRepository {
    suspend fun recordLogin(
        uid: String,
        input: RecordLoginInput,
    )

    suspend fun getHistory(
        uid: String,
        limit: Int = 5,
    ): List<LoginEvent>
}
