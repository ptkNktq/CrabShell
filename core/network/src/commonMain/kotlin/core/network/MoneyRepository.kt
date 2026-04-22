package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.MonthlyMoneyStatusUpdate
import model.PaymentRecord

interface MoneyRepository {
    suspend fun getMonthlyMoney(month: String): MonthlyMoney

    suspend fun getMyMonthlyMoney(month: String): MonthlyMoney

    suspend fun saveMonthlyMoney(data: MonthlyMoney)

    suspend fun recordPayment(
        month: String,
        record: PaymentRecord,
    ): MonthlyMoney

    suspend fun updateStatus(
        month: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoney

    suspend fun importRecurringItems(month: String): MonthlyMoney
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

    override suspend fun updateStatus(
        month: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoney =
        client
            .patch("/api/money/$month/status") {
                contentType(ContentType.Application.Json)
                setBody(MonthlyMoneyStatusUpdate(status))
            }.body()

    override suspend fun importRecurringItems(month: String): MonthlyMoney = client.post("/api/money/$month/import-by-tag").body()
}
