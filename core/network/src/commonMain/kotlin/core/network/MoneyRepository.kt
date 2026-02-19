package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.MonthlyMoney
import model.PaymentRecord

interface MoneyRepository {
    suspend fun getMonthlyMoney(month: String): MonthlyMoney

    suspend fun getMyMonthlyMoney(month: String): MonthlyMoney

    suspend fun saveMonthlyMoney(data: MonthlyMoney)

    suspend fun recordPayment(
        month: String,
        record: PaymentRecord,
    ): MonthlyMoney

    suspend fun toggleLock(month: String): MonthlyMoney
}

class MoneyRepositoryImpl(
    private val client: HttpClient,
) : MoneyRepository {
    override suspend fun getMonthlyMoney(month: String): MonthlyMoney = client.get("/api/money/$month").body()

    override suspend fun getMyMonthlyMoney(month: String): MonthlyMoney = client.get("/api/money/$month/my").body()

    override suspend fun saveMonthlyMoney(data: MonthlyMoney) {
        client.put("/api/money/${data.month}") {
            contentType(ContentType.Application.Json)
            setBody(data)
        }
    }

    override suspend fun recordPayment(
        month: String,
        record: PaymentRecord,
    ): MonthlyMoney =
        client
            .post("/api/money/$month/pay") {
                contentType(ContentType.Application.Json)
                setBody(record)
            }.body()

    override suspend fun toggleLock(month: String): MonthlyMoney = client.patch("/api/money/$month/lock").body()
}
