package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.Share
import server.money.MoneyRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentMcpToolsTest {
    private val moneyRepository = mockk<MoneyRepository>()

    // JST 2026-04-01 08:00（UTC ではまだ 3 月）
    private val now = Instant.parse("2026-03-31T23:00:00Z")
    private val tools = PaymentMcpTools(moneyRepository) { now }

    private val uid = "uid1"

    private fun args(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    private fun CallToolResult.summary(): PaymentMcpTools.PaymentSummaryResult =
        Json.decodeFromString(PaymentMcpTools.PaymentSummaryResult.serializer(), text())

    private fun item(
        id: String,
        name: String,
        dueDate: String?,
        vararg shares: Pair<String, Long>,
    ) = MoneyItem(
        id = id,
        name = name,
        amount = shares.sumOf { it.second },
        note = "メモ-$id",
        shares = shares.map { (uid, amount) -> Share(uid, amount) },
        dueDate = dueDate,
    )

    @Test
    fun summarizesOwnSharesByDueDate() =
        runTest {
            coEvery { moneyRepository.getMonthlyMoney("2026-04") } returns
                MonthlyMoney(
                    yearMonth = "2026-04",
                    items =
                        listOf(
                            item("1", "家賃", "2026-04-27", uid to 50000L, "uid2" to 50000L),
                            item("2", "電気代", "2026-04-10", uid to 3000L, "uid2" to 3000L),
                            item("3", "水道代", "2026-04-27", uid to 2000L),
                            item("4", "雑費", null, uid to 1500L),
                            // 自分の負担がない項目は含めない
                            item("5", "他人の項目", "2026-04-05", "uid2" to 9999L),
                        ),
                    payments =
                        listOf(
                            Payment(id = "p1", uid = uid, amount = 10000L, paidAt = "2026-04-01T00:00:00Z"),
                            Payment(id = "p2", uid = uid, amount = 2000L, paidAt = "2026-04-02T00:00:00Z", isRedemption = true),
                            // 他のユーザーの振込は含めない
                            Payment(id = "p3", uid = "uid2", amount = 53000L, paidAt = "2026-04-03T00:00:00Z"),
                        ),
                )

            val result = tools.getPaymentSummary(uid, null)

            assertEquals(null, result.isError)
            assertEquals(
                PaymentMcpTools.PaymentSummaryResult(
                    yearMonth = "2026-04",
                    allocatedTotal = 56500L,
                    allocatedByDueDate =
                        listOf(
                            PaymentMcpTools.DueDateAmount("2026-04-10", 3000L),
                            PaymentMcpTools.DueDateAmount("2026-04-27", 52000L),
                            PaymentMcpTools.DueDateAmount(null, 1500L),
                        ),
                    // 過払い金からの精算分も振込として数える
                    paidTotal = 12000L,
                ),
                result.summary(),
            )
            // 期日なしも null として明示的に出力する
            assertTrue("\"dueDate\":null" in result.text())
            // 合計・期日ごとの金額・振込総額以外（項目名・メモ・他人の負担額・個々の振込など）は渡さない
            val json = Json.parseToJsonElement(result.text()).jsonObject
            assertEquals(setOf("yearMonth", "allocatedTotal", "allocatedByDueDate", "paidTotal"), json.keys)
            json.getValue("allocatedByDueDate").jsonArray.forEach { assertEquals(setOf("dueDate", "amount"), it.jsonObject.keys) }
        }

    @Test
    fun usesSpecifiedYearMonth() =
        runTest {
            coEvery { moneyRepository.getMonthlyMoney("2026-02") } returns
                MonthlyMoney(yearMonth = "2026-02", items = listOf(item("1", "家賃", "2026-02-27", uid to 50000L)))

            val summary = tools.getPaymentSummary(uid, args("yearMonth" to "2026-02")).summary()

            assertEquals("2026-02", summary.yearMonth)
            assertEquals(50000L, summary.allocatedTotal)
        }

    @Test
    fun nullYearMonthIsTreatedAsCurrentMonth() =
        runTest {
            coEvery { moneyRepository.getMonthlyMoney("2026-04") } returns null

            assertEquals("2026-04", tools.getPaymentSummary(uid, buildJsonObject { put("yearMonth", JsonNull) }).summary().yearMonth)
        }

    @Test
    fun monthWithoutDataReturnsZero() =
        runTest {
            coEvery { moneyRepository.getMonthlyMoney("2026-04") } returns null

            assertEquals(
                PaymentMcpTools.PaymentSummaryResult(
                    yearMonth = "2026-04",
                    allocatedTotal = 0L,
                    allocatedByDueDate = emptyList(),
                    paidTotal = 0L,
                ),
                tools.getPaymentSummary(uid, null).summary(),
            )
        }

    @Test
    fun zeroShareIsOmitted() =
        runTest {
            coEvery { moneyRepository.getMonthlyMoney("2026-04") } returns
                MonthlyMoney(yearMonth = "2026-04", items = listOf(item("1", "家賃", "2026-04-27", uid to 0L, "uid2" to 50000L)))

            assertEquals(emptyList(), tools.getPaymentSummary(uid, null).summary().allocatedByDueDate)
        }

    @Test
    fun invalidYearMonthIsRejectedWithoutReading() =
        runTest {
            val invalidCalls =
                listOf<suspend () -> Unit>(
                    { tools.getPaymentSummary(uid, args("yearMonth" to "2026/04")) },
                    { tools.getPaymentSummary(uid, args("yearMonth" to "2026-4")) },
                    { tools.getPaymentSummary(uid, args("yearMonth" to "2026-13")) },
                    { tools.getPaymentSummary(uid, args("yearMonth" to "+10000-01")) },
                    { tools.getPaymentSummary(uid, buildJsonObject { put("yearMonth", JsonPrimitive(202604)) }) },
                )
            invalidCalls.forEach { call ->
                assertTrue(runCatching { call() }.exceptionOrNull() is InvalidToolArgumentException)
            }
            coVerify(exactly = 0) { moneyRepository.getMonthlyMoney(any()) }
        }
}
