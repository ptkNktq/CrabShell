package server.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import model.Feeding
import model.FeedingLog
import model.MealTime
import model.Pet
import org.slf4j.LoggerFactory
import server.feeding.FeedingRepository
import server.feeding.feedingDate
import server.pet.PetRepository
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * MCP で公開する給餌のツール群（給餌状況の取得・給餌の記録・メモの更新）。
 *
 * 操作するペットは引数で受け取らず、呼び出したユーザーがメンバーになっているペットから自動で選ぶ。
 */
class FeedingMcpTools(
    private val feedingRepository: FeedingRepository,
    private val petRepository: PetRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(FeedingMcpTools::class.java)

    /** [uid] のユーザーとして実行するツールを [server] に登録する */
    fun register(
        server: Server,
        uid: String,
    ) {
        server.addTool(
            name = GET_FEEDING_LOG,
            description = "ペットのごはんの記録（朝・昼・晩それぞれをあげたかどうかと時刻、その日のメモ）を取得する。",
            inputSchema = ToolSchema(properties = buildJsonObject { putDateProperty() }),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
        ) { request -> handle(GET_FEEDING_LOG, uid) { getFeedingLog(uid, request.arguments) } }

        server.addTool(
            name = RECORD_FEEDING,
            description = "ペットにごはんをあげたことを、現在時刻で記録する。すでに記録済みの場合は上書きしない。",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putDateProperty()
                            putJsonObject(ARG_MEAL_TIME) {
                                put("type", "string")
                                putJsonArray("enum") { MealTime.entries.forEach { add(it.name) } }
                                put("description", "MORNING（朝）/ LUNCH（昼）/ EVENING（晩）")
                            }
                        },
                    required = listOf(ARG_MEAL_TIME),
                ),
            toolAnnotations = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        ) { request -> handle(RECORD_FEEDING, uid) { recordFeeding(uid, request.arguments) } }

        server.addTool(
            name = UPDATE_FEEDING_NOTE,
            description = "ペットのごはんのその日のメモを、指定した内容で置き換える。",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putDateProperty()
                            putJsonObject(ARG_NOTE) {
                                put("type", "string")
                                put("description", "メモの内容。既存のメモはこの内容で置き換わる")
                            }
                        },
                    required = listOf(ARG_NOTE),
                ),
            toolAnnotations = ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false),
        ) { request -> handle(UPDATE_FEEDING_NOTE, uid) { updateNote(uid, request.arguments) } }
    }

    internal suspend fun getFeedingLog(
        uid: String,
        arguments: JsonObject?,
    ): CallToolResult {
        val pet = selectPet(uid)
        val date = parseDate(arguments)
        return success(feedingRepository.getFeedingLog(pet.id, date).toResult(pet))
    }

    internal suspend fun recordFeeding(
        uid: String,
        arguments: JsonObject?,
    ): CallToolResult {
        val pet = selectPet(uid)
        val date = parseDate(arguments)
        val mealTime = parseMealTime(arguments)
        val current = feedingRepository.getFeedingLog(pet.id, date)
        if (current.feedings[mealTime]?.done != true) {
            feedingRepository.recordFeeding(pet.id, date, mealTime, now().toString())
        }
        return success(feedingRepository.getFeedingLog(pet.id, date).toResult(pet))
    }

    internal suspend fun updateNote(
        uid: String,
        arguments: JsonObject?,
    ): CallToolResult {
        val pet = selectPet(uid)
        val date = parseDate(arguments)
        val note = arguments.string(ARG_NOTE) ?: throw InvalidToolArgumentException("$ARG_NOTE を指定してください")
        feedingRepository.updateNote(pet.id, date, note)
        return success(feedingRepository.getFeedingLog(pet.id, date).toResult(pet))
    }

    /**
     * 操作対象のペットを選ぶ。
     *
     * TODO: 複数匹は考慮していない。メンバーになっているペットが複数いる場合も先頭の 1 匹を選ぶ。
     *  複数匹に対応するときは、ペットを引数で指定できるようにする（1 匹だけなら省略可のまま）。
     */
    private suspend fun selectPet(uid: String): Pet =
        petRepository.getPetsForMember(uid).firstOrNull()
            ?: throw InvalidToolArgumentException("メンバーになっているペットがいません")

    /** 日付の指定がなければ現在の給餌日付（JST 5:00 で切り替わる）にする */
    private fun parseDate(arguments: JsonObject?): String {
        val value = arguments.string(ARG_DATE) ?: return feedingDate(now())
        return try {
            LocalDate.parse(value).toString()
        } catch (_: DateTimeParseException) {
            throw InvalidToolArgumentException("$ARG_DATE は YYYY-MM-DD 形式で指定してください: $value")
        }
    }

    private fun parseMealTime(arguments: JsonObject?): MealTime {
        val value = arguments.string(ARG_MEAL_TIME) ?: throw InvalidToolArgumentException("$ARG_MEAL_TIME を指定してください")
        return MealTime.entries.find { it.name == value }
            ?: throw InvalidToolArgumentException("$ARG_MEAL_TIME は ${MealTime.entries.joinToString(" / ")} のいずれかを指定してください: $value")
    }

    /**
     * ツールを実行し、監査ログを残す。
     * 引数の誤りは呼び出し側（AI）が直せるようにメッセージを返し、想定外の例外は内部情報を出さずに固定メッセージを返す。
     */
    private suspend fun handle(
        toolName: String,
        uid: String,
        block: suspend () -> CallToolResult,
    ): CallToolResult =
        try {
            block().also { logger.info("MCP tool called: tool={} uid={}", toolName, uid) }
        } catch (e: InvalidToolArgumentException) {
            logger.info("MCP tool rejected: tool={} uid={} reason={}", toolName, uid, e.message)
            error(e.message ?: "引数が正しくありません")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // SDK に任せると例外メッセージ（Firestore のパス等）がそのままクライアントに返るため、ここで握る
            logger.error("MCP tool failed: tool={} uid={}", toolName, uid, e)
            error(TOOL_FAILED_MESSAGE)
        }

    private fun success(result: FeedingLogResult): CallToolResult =
        CallToolResult(content = listOf(TextContent(json.encodeToString(FeedingLogResult.serializer(), result))))

    private fun error(message: String): CallToolResult = CallToolResult(content = listOf(TextContent(message)), isError = true)

    private fun FeedingLog.toResult(pet: Pet) =
        FeedingLogResult(
            petName = pet.name,
            date = date,
            note = note,
            feedings = feedings,
        )

    /** ツールの結果として返す給餌の記録 */
    @Serializable
    internal data class FeedingLogResult(
        val petName: String,
        val date: String,
        val note: String,
        val feedings: Map<MealTime, Feeding>,
    )

    /** ツールの引数が正しくないことを表す。メッセージは MCP クライアントにそのまま返す */
    private class InvalidToolArgumentException(
        message: String,
    ) : RuntimeException(message)

    companion object {
        internal const val GET_FEEDING_LOG = "get_feeding_log"
        internal const val RECORD_FEEDING = "record_feeding"
        internal const val UPDATE_FEEDING_NOTE = "update_feeding_note"

        internal const val TOOL_FAILED_MESSAGE = "処理に失敗しました。時間をおいて再度お試しください"

        private const val ARG_DATE = "date"
        private const val ARG_MEAL_TIME = "mealTime"
        private const val ARG_NOTE = "note"

        // 未給餌（done = false）も省略せず出力し、AI が読み違えないようにする
        private val json = Json { encodeDefaults = true }

        private fun JsonObjectBuilder.putDateProperty() {
            putJsonObject(ARG_DATE) {
                put("type", "string")
                put("description", "日付（YYYY-MM-DD）。省略すると今日（JST 5:00 で日付が切り替わる）")
            }
        }

        private fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
