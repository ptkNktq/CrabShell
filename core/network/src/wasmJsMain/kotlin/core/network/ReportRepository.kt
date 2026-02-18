package core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import model.ExpenseReport

interface ReportRepository {
    suspend fun getExpenseReport(center: String): ExpenseReport
}

class ReportRepositoryImpl(private val client: HttpClient) : ReportRepository {
    override suspend fun getExpenseReport(center: String): ExpenseReport = client.get("/api/report?center=$center&range=3").body()
}
