package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.MonthlyMoney
import model.MonthlyMoneySaveRequest
import model.MonthlyMoneyStatus
import model.MonthlyMoneyStatusUpdateRequest
import model.PayRequest

interface MoneyRepository {
    suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoney

    suspend fun getMyMonthlyMoney(yearMonth: String): MonthlyMoney

    suspend fun saveMonthlyMoney(
        yearMonth: String,
        request: MonthlyMoneySaveRequest,
    ): MonthlyMoney

    suspend fun recordPayment(
        yearMonth: String,
        request: PayRequest,
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

    override suspend fun saveMonthlyMoney(
        yearMonth: String,
        request: MonthlyMoneySaveRequest,
    ): MonthlyMoney =
        client
            .put("/api/money/$yearMonth") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun recordPayment(
        yearMonth: String,
        request: PayRequest,
    ): MonthlyMoney =
        client
            .post("/api/money/$yearMonth/pay") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun updateStatus(
        yearMonth: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoney =
        client
            .patch("/api/money/$yearMonth/status") {
                contentType(ContentType.Application.Json)
                setBody(MonthlyMoneyStatusUpdateRequest(status))
            }.body()

    override suspend fun importRecurringItems(yearMonth: String): MonthlyMoney = client.post("/api/money/$yearMonth/import-by-tag").body()
}
