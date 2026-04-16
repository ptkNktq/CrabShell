package server.loginhistory

import model.LoginEvent
import java.time.Instant

interface LoginHistoryRepository {
    suspend fun recordLogin(
        uid: String,
        event: LoginEvent,
        expireAt: Instant,
    )

    suspend fun getHistory(
        uid: String,
        limit: Int = 5,
    ): List<LoginEvent>
}
