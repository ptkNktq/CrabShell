package server.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import model.MonthlyMoney
import model.groupedByDueDate
import model.myShareTotal
import org.slf4j.LoggerFactory
import server.money.MoneyRepository
import server.money.filterForUser
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private val JST = ZoneId.of("Asia/Tokyo")

/**
 * MCP で公開する支払いのツール群（自分の支払額の概要の取得）。
 *
 * 返すのは指定月の負担額の合計と、支払期日ごとの金額だけにする。
 * 項目名・メモ・他のユーザーの負担額・振込記録などの詳細は、AI クライアントに渡さない。
 */
class PaymentMcpTools(
    private val moneyRepository: MoneyRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(PaymentMcpTools::class.java)

    /** [uid] のユーザーとして実行するツールを [server] に登録する */
    fun register(
        server: Server,
        uid: String,
    ) {
        server.addTool(
            name = GET_PAYMENT_SUMMARY,
            description =
                "指定した月に自分が支払う金額の概要（合計額と、支払期日ごとの金額）を取得する。" +
                    "項目の内訳は返さない。dueDate が null の金額は支払期日が決まっていない分。",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject(ARG_YEAR_MONTH) {
                                put("type", "string")
                                put("description", "年月（YYYY-MM）。省略すると今月（JST）")
                            }
                        },
                ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
        ) { request -> runMcpTool(logger, GET_PAYMENT_SUMMARY, uid) { getPaymentSummary(uid, request.arguments) } }
    }

    internal suspend fun getPaymentSummary(
        uid: String,
        arguments: JsonObject?,
    ): CallToolResult {
        val yearMonth = parseYearMonth(arguments)
        val monthlyMoney = moneyRepository.getMonthlyMoney(yearMonth) ?: MonthlyMoney(yearMonth = yearMonth)
        val byDueDate =
            monthlyMoney
                .filterForUser(uid)
                .items
                .groupedByDueDate()
                .map { (dueDate, group) -> DueDateAmount(dueDate = dueDate, amount = group.myShareTotal(uid)) }
                .filter { it.amount != 0L }
        val summary = PaymentSummaryResult(yearMonth = yearMonth, total = byDueDate.sumOf { it.amount }, byDueDate = byDueDate)
        return toolSuccessResult(Json.encodeToString(PaymentSummaryResult.serializer(), summary))
    }

    private fun parseYearMonth(arguments: JsonObject?): String {
        val value = arguments.stringArgument(ARG_YEAR_MONTH) ?: return YearMonth.from(now().atZone(JST)).toString()
        // YearMonth.parse は "+10000-01" のような 4 桁を超える年も受け付けるため、形式は正規表現で絞る
        if (!YEAR_MONTH_PATTERN.matches(value)) {
            throw InvalidToolArgumentException("$ARG_YEAR_MONTH は YYYY-MM 形式で指定してください: $value")
        }
        return value
    }

    /** get_payment_summary の結果。指定月の自分の負担額の概要 */
    @Serializable
    internal data class PaymentSummaryResult(
        val yearMonth: String,
        /** 負担額の合計 */
        val total: Long,
        /** 支払期日ごとの負担額。期日の昇順で、期日なし（null）は最後 */
        val byDueDate: List<DueDateAmount>,
    )

    @Serializable
    internal data class DueDateAmount(
        /** 支払期日（YYYY-MM-DD）。期日が決まっていない分は null */
        val dueDate: String?,
        val amount: Long,
    )

    companion object {
        internal const val GET_PAYMENT_SUMMARY = "get_payment_summary"

        private const val ARG_YEAR_MONTH = "yearMonth"

        private val YEAR_MONTH_PATTERN = Regex("""\d{4}-(0[1-9]|1[0-2])""")
    }
}
