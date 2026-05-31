package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.MonthlyMoneyResponse
import model.MonthlyMoneySaveRequest
import model.MonthlyMoneyStatus
import model.MonthlyMoneyStatusUpdateRequest
import model.PayRequest

interface MoneyRepository {
    suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoneyResponse

    suspend fun getMyMonthlyMoney(yearMonth: String): MonthlyMoneyResponse

    suspend fun saveMonthlyMoney(
        yearMonth: String,
        request: MonthlyMoneySaveRequest,
    ): MonthlyMoneyResponse

    suspend fun recordPayment(
        yearMonth: String,
        request: PayRequest,
    ): MonthlyMoneyResponse

    suspend fun updateStatus(
        yearMonth: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoneyResponse

    suspend fun importRecurringItems(yearMonth: String): MonthlyMoneyResponse
}

class MoneyRepositoryImpl(
    private val client: HttpClient,
) : MoneyRepository {
    override suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoneyResponse = client.get("/api/money/$yearMonth").body()

    override suspend fun getMyMonthlyMoney(yearMonth: String): MonthlyMoneyResponse = client.get("/api/money/$yearMonth/my").body()

    override suspend fun saveMonthlyMoney(
        yearMonth: String,
        request: MonthlyMoneySaveRequest,
    ): MonthlyMoneyResponse =
        client
            .put("/api/money/$yearMonth") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun recordPayment(
        yearMonth: String,
        request: PayRequest,
    ): MonthlyMoneyResponse =
        client
            .post("/api/money/$yearMonth/pay") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun updateStatus(
        yearMonth: String,
        status: MonthlyMoneyStatus,
    ): MonthlyMoneyResponse =
        client
            .patch("/api/money/$yearMonth/status") {
                contentType(ContentType.Application.Json)
                setBody(MonthlyMoneyStatusUpdateRequest(status))
            }.body()

    override suspend fun importRecurringItems(yearMonth: String): MonthlyMoneyResponse =
        client.post("/api/money/$yearMonth/import-by-tag").body()
}
