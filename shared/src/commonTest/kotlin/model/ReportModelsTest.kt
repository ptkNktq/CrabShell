package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportModelsTest {
    private val json = Json

    @Test
    fun expenseItemRoundTrip() {
        val item = ExpenseItem(name = "家賃", amount = 80000L, note = "管理費込み")
        val encoded = json.encodeToString(ExpenseItem.serializer(), item)
        val decoded = json.decodeFromString(ExpenseItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun expenseItemDefaultNote() {
        val jsonStr = """{"name":"家賃","amount":80000}"""
        val decoded = json.decodeFromString(ExpenseItem.serializer(), jsonStr)
        assertEquals("", decoded.note)
    }

    @Test
    fun monthlyExpenseSummaryRoundTrip() {
        val summary =
            MonthlyExpenseSummary(
                month = "2025-01",
                totalAmount = 150000L,
                items =
                    listOf(
                        ExpenseItem(name = "家賃", amount = 80000L),
                        ExpenseItem(name = "光熱費", amount = 15000L),
                    ),
            )
        val encoded = json.encodeToString(MonthlyExpenseSummary.serializer(), summary)
        val decoded = json.decodeFromString(MonthlyExpenseSummary.serializer(), encoded)
        assertEquals(summary, decoded)
    }

    @Test
    fun monthlyExpenseSummaryDefaultItems() {
        val jsonStr = """{"month":"2025-02","totalAmount":50000}"""
        val decoded = json.decodeFromString(MonthlyExpenseSummary.serializer(), jsonStr)
        assertEquals("2025-02", decoded.month)
        assertEquals(50000L, decoded.totalAmount)
        assertEquals(emptyList(), decoded.items)
    }

    @Test
    fun expenseReportRoundTrip() {
        val report =
            ExpenseReport(
                months =
                    listOf(
                        MonthlyExpenseSummary(
                            month = "2025-01",
                            totalAmount = 100000L,
                            items = listOf(ExpenseItem(name = "食費", amount = 40000L)),
                        ),
                        MonthlyExpenseSummary(
                            month = "2025-02",
                            totalAmount = 120000L,
                        ),
                    ),
            )
        val encoded = json.encodeToString(ExpenseReport.serializer(), report)
        val decoded = json.decodeFromString(ExpenseReport.serializer(), encoded)
        assertEquals(report, decoded)
    }

    @Test
    fun expenseReportDefaultMonths() {
        val jsonStr = """{}"""
        val decoded = json.decodeFromString(ExpenseReport.serializer(), jsonStr)
        assertEquals(emptyList(), decoded.months)
    }
}
