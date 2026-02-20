package server.quest

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val geminiModel = System.getenv("GEMINI_MODEL") ?: "gemini-2.5-flash"

private val GEMINI_API_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent"

private val SYSTEM_PROMPT =
    """
    あなたはファンタジー世界のギルドの受付係です。
    依頼者から受け取った情報をもとに、ギルドの掲示板に貼り出すクエストを作成してください。

    ルール:
    - 日本語で書くこと
    - title: 元のタイトルをRPG風にアレンジした短いタイトル（20文字以内）
    - description: 3〜5文程度の依頼文
    - ファンタジーRPG風の言い回しを使うこと（例: 「冒険者よ」「報酬」「ギルド」）
    - 実際の家事内容が伝わるようにすること
    - 依頼者が説明文に書いた要望や詳細は必ず全て含めること
    - 大げさすぎず、ユーモアを交えること
    - マークダウン記法は使わないこと（純粋なテキストのみ）
    """.trimIndent()

/** JSON モードで title + description を返すための generationConfig */
private val GENERATION_CONFIG =
    buildJsonObject {
        put("responseMimeType", "application/json")
        putJsonObject("responseSchema") {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "STRING") }
                putJsonObject("description") { put("type", "STRING") }
            }
            putJsonArray("required") {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("description"))
            }
        }
    }

private val jsonParser = Json { ignoreUnknownKeys = true }

class GeminiTextGenerator(
    private val apiKey: String,
    private val client: HttpClient,
) : QuestTextGenerator {
    override suspend fun generate(input: QuestTextInput): GeneratedQuestText {
        val userPrompt =
            buildString {
                appendLine("タイトル: ${input.title}")
                appendLine("カテゴリ: ${input.category.name}")
                appendLine("報酬ポイント: ${input.rewardPoints}")
                input.deadline?.let { appendLine("期限: $it") }
                if (input.description.isNotBlank()) {
                    appendLine("説明・要望: ${input.description}")
                }
            }

        val response =
            client.post("$GEMINI_API_URL?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = SYSTEM_PROMPT))),
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
                        generationConfig = GENERATION_CONFIG,
                    ),
                )
            }

        val json = response.body<JsonObject>()

        // API エラーレスポンスのハンドリング
        json["error"]?.jsonObject?.let { error ->
            val status = error["status"]?.jsonPrimitive?.content ?: ""
            val message =
                if (status == "RESOURCE_EXHAUSTED") {
                    "API の利用制限に達しました。しばらく待ってから再試行してください"
                } else {
                    error["message"]?.jsonPrimitive?.content ?: "Gemini API エラー"
                }
            throw IllegalStateException(message)
        }

        val text =
            json["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?: throw IllegalStateException("Gemini レスポンスの解析に失敗しました")

        // JSON モードのレスポンスをパース
        val parsed = jsonParser.decodeFromString<GeminiGeneratedQuest>(text)
        return GeneratedQuestText(
            title = parsed.title,
            description = parsed.description,
        )
    }
}

@Serializable
private data class GeminiGeneratedQuest(
    val title: String,
    val description: String,
)

@Serializable
private data class GeminiRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    @SerialName("generation_config") val generationConfig: JsonObject,
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String,
)
