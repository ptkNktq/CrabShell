package core.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.MonthlyMoney
import model.PaymentRecord

object MoneyRepository {
    suspend fun getMonthlyMoney(month: String): MonthlyMoney = authenticatedClient.get("/api/money/$month").body()

    suspend fun getMyMonthlyMoney(month: String): MonthlyMoney = authenticatedClient.get("/api/money/$month/my").body()

    suspend fun saveMonthlyMoney(data: MonthlyMoney) {
        authenticatedClient.put("/api/money/${data.month}") {
            contentType(ContentType.Application.Json)
            setBody(data)
        }
    }

    suspend fun recordPayment(
        month: String,
        record: PaymentRecord,
    ): MonthlyMoney =
        authenticatedClient.post("/api/money/$month/pay") {
            contentType(ContentType.Application.Json)
            setBody(record)
        }.body()
}
