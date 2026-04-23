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
    suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoney

    suspend fun getMyMonthlyMoney(yearMonth: String): MonthlyMoney

    suspend fun saveMonthlyMoney(data: MonthlyMoney)

    suspend fun recordPayment(
        yearMonth: String,
        record: PaymentRecord,
    ): MonthlyMoney

    suspend fun updateStatus(
        yearMonth: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoney

    suspend fun importRecurringItems(yearMonth: String): MonthlyMoney
}

class MoneyRepositoryImpl(
    private val client: HttpClient,
) : MoneyRepository {
    override suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoney = client.get("/api/money/$yearMonth").body()

    override suspend fun getMyMonthlyMoney(yearMonth: String): MonthlyMoney = client.get("/api/money/$yearMonth/my").body()

    override suspend fun saveMonthlyMoney(data: MonthlyMoney) {
        client.put("/api/money/${data.yearMonth}") {
            contentType(ContentType.Application.Json)
            setBody(data)
        }
    }

    override suspend fun recordPayment(
        yearMonth: String,
        record: PaymentRecord,
    ): MonthlyMoney =
        client
            .post("/api/money/$yearMonth/pay") {
                contentType(ContentType.Application.Json)
                setBody(record)
            }.body()

    override suspend fun updateStatus(
        yearMonth: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoney =
        client
            .patch("/api/money/$yearMonth/status") {
                contentType(ContentType.Application.Json)
                setBody(MonthlyMoneyStatusUpdate(status))
            }.body()

    override suspend fun importRecurringItems(yearMonth: String): MonthlyMoney = client.post("/api/money/$yearMonth/import-by-tag").body()
}
