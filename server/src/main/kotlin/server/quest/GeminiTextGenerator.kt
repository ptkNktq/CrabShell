package server.quest

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val GEMINI_API_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

private val SYSTEM_PROMPT =
    """
    あなたはファンタジー世界のギルドの受付係です。
    依頼者から受け取った情報をもとに、ギルドの掲示板に貼り出すクエスト依頼文を作成してください。

    ルール:
    - 日本語で書くこと
    - 3〜5文程度の短い文章にすること
    - ファンタジーRPG風の言い回しを使うこと（例: 「冒険者よ」「報酬」「ギルド」）
    - 実際の家事内容が伝わるようにすること
    - 大げさすぎず、ユーモアを交えること
    """.trimIndent()

class GeminiTextGenerator(
    private val apiKey: String,
    private val client: HttpClient,
) : QuestTextGenerator {
    override suspend fun generate(input: QuestTextInput): String {
        val userPrompt =
            buildString {
                appendLine("タイトル: ${input.title}")
                appendLine("カテゴリ: ${input.category.name}")
                appendLine("報酬ポイント: ${input.rewardPoints}")
                input.deadline?.let { appendLine("期限: $it") }
            }

        val response =
            client.post("$GEMINI_API_URL?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = SYSTEM_PROMPT))),
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
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

        return json["candidates"]
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
    }
}

@Serializable
private data class GeminiRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String,
)
