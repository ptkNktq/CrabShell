package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import model.BalanceSummary
import model.ExpenseReport
import model.OverpaymentRedemptionRequest

interface ReportRepository {
    suspend fun getExpenseReport(center: String): ExpenseReport

    suspend fun getBalanceSummary(): BalanceSummary

    suspend fun redeemOverpayment(request: OverpaymentRedemptionRequest)
}

class ReportRepositoryImpl(
    private val client: HttpClient,
) : ReportRepository {
    override suspend fun getExpenseReport(center: String): ExpenseReport = client.get("/api/report?center=$center&range=3").body()

    override suspend fun getBalanceSummary(): BalanceSummary = client.get("/api/report/balances").body()

    override suspend fun redeemOverpayment(request: OverpaymentRedemptionRequest) {
        client.post("/api/report/balances/redeem") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
