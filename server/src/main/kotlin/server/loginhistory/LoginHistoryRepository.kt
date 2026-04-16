package server.loginhistory

import model.LoginEvent

interface LoginHistoryRepository {
    suspend fun recordLogin(
        uid: String,
        event: LoginEvent,
    )

    suspend fun getHistory(
        uid: String,
        limit: Int = 50,
    ): List<LoginEvent>
}
