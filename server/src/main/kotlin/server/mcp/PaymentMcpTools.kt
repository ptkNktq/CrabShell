package server.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.Serializable
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
 * 返すのは指定月の請求額（自分の負担額）の合計・支払期日ごとの請求額・振込済みの総額だけにする。
 * 項目名・メモ・他のユーザーの負担額・個々の振込（いついくら振り込んだか）などの詳細は、AI クライアントに渡さない。
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
                "指定した月の自分の支払いの概要を取得する。" +
                    "billedTotal は請求額（自分の負担額）の合計、billedByDueDate は支払期日ごとの請求額、" +
                    "paidTotal はその月に振り込んだ総額。dueDate が null の請求額は支払期日が決まっていない分。",
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
        val myMoney = (moneyRepository.getMonthlyMoney(yearMonth) ?: MonthlyMoney(yearMonth = yearMonth)).filterForUser(uid)
        val billedByDueDate =
            myMoney
                .items
                .groupedByDueDate()
                .map { (dueDate, group) -> DueDateAmount(dueDate = dueDate, amount = group.myShareTotal(uid)) }
                .filter { it.amount != 0L }
        val summary =
            PaymentSummaryResult(
                yearMonth = yearMonth,
                billedTotal = billedByDueDate.sumOf { it.amount },
                billedByDueDate = billedByDueDate,
                // 支払い画面と同じく、過払い金からの精算分も振込として数える
                paidTotal = myMoney.payments.sumOf { it.amount },
            )
        return toolSuccessResult(McpToolResultJson.encodeToString(PaymentSummaryResult.serializer(), summary))
    }

    private fun parseYearMonth(arguments: JsonObject?): String {
        val value = arguments.stringArgument(ARG_YEAR_MONTH) ?: return YearMonth.from(now().atZone(JST)).toString()
        // YearMonth.parse は "+10000-01" のような 4 桁を超える年も受け付けるため、形式は正規表現で絞る
        if (!YEAR_MONTH_PATTERN.matches(value)) {
            throw InvalidToolArgumentException("$ARG_YEAR_MONTH は YYYY-MM 形式で指定してください: $value")
        }
        return value
    }

    /** get_payment_summary の結果。指定月の自分の支払いの概要 */
    @Serializable
    internal data class PaymentSummaryResult(
        val yearMonth: String,
        /** 請求額（自分の負担額）の合計 */
        val billedTotal: Long,
        /** 支払期日ごとの請求額。期日の昇順で、期日なし（null）は最後 */
        val billedByDueDate: List<DueDateAmount>,
        /** その月に振り込んだ総額（過払い金からの精算分を含む） */
        val paidTotal: Long,
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
