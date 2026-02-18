package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import model.ExpenseReport

interface ReportRepository {
    suspend fun getExpenseReport(months: Int = 6): ExpenseReport
}

class ReportRepositoryImpl(private val client: HttpClient) : ReportRepository {
    override suspend fun getExpenseReport(months: Int): ExpenseReport =
        client.get("/api/report?months=$months").body()
}
