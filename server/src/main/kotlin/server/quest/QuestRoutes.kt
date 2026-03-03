package server.quest

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import model.CreateQuestRequest
import model.GenerateQuestTextRequest
import model.GenerateQuestTextResponse
import model.Quest
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.config.EnvConfig
import server.ratelimit.RateLimitNames

private val geminiApiKey: String? = EnvConfig["GEMINI_API_KEY"]

private val questTextGenerator: QuestTextGenerator? by lazy {
    geminiApiKey?.let { key ->
        GeminiTextGenerator(
            apiKey = key,
            client =
                HttpClient {
                    install(ContentNegotiation) { json() }
                },
        )
    }
}

fun Route.questRoutes() {
    val questService by inject<QuestService>()

    route("/quests") {
        authenticated {
            get({
                tags = listOf("quest")
                summary = "クエスト一覧取得"
                request {
                    queryParameter<String>("status") {
                        description = "ステータスでフィルタ"
                        required = false
                    }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<List<Quest>>()
                    }
                }
            }) {
                val statusFilter = call.request.queryParameters["status"]
                val quests = questService.listQuests(statusFilter)
                call.respond(quests)
            }

            post({
                tags = listOf("quest")
                summary = "クエスト作成"
                request {
                    body<CreateQuestRequest>()
                }
                response {
                    code(HttpStatusCode.Created) {
                        body<Quest>()
                    }
                    code(HttpStatusCode.Conflict) { description = "同時発行上限" }
                }
            }) {
                val token = call.firebasePrincipal
                val request = call.receive<CreateQuestRequest>()

                when (val result = questService.createQuest(request, token.uid, token.name ?: "")) {
                    is QuestResult.Success -> call.respond(HttpStatusCode.Created, result.data)
                    is QuestResult.Conflict -> call.respond(HttpStatusCode.Conflict, mapOf("error" to result.message))
                    is QuestResult.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to result.message))
                    is QuestResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to result.message))
                }
            }

            put("/{id}/accept", {
                tags = listOf("quest")
                summary = "クエスト受注"
                request {
                    pathParameter<String>("id") { description = "クエスト ID" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<Quest>()
                    }
                    code(HttpStatusCode.NotFound) { description = "クエスト未発見" }
                    code(HttpStatusCode.Conflict) { description = "受注不可" }
                    code(HttpStatusCode.Forbidden) { description = "自分のクエストは受注不可" }
                }
            }) {
                val token = call.firebasePrincipal
                val id = call.parameters.getOrFail("id")

                when (val result = questService.acceptQuest(id, token.uid, token.name ?: "")) {
                    is QuestResult.Success -> call.respond(result.data)
                    is QuestResult.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to result.message))
                    is QuestResult.Conflict -> call.respond(HttpStatusCode.Conflict, mapOf("error" to result.message))
                    is QuestResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to result.message))
                }
            }

            put("/{id}/verify", {
                tags = listOf("quest")
                summary = "クエスト達成承認"
                request {
                    pathParameter<String>("id") { description = "クエスト ID" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<Quest>()
                    }
                    code(HttpStatusCode.NotFound) { description = "クエスト未発見" }
                    code(HttpStatusCode.Conflict) { description = "承認不可" }
                    code(HttpStatusCode.Forbidden) { description = "作成者のみ承認可" }
                }
            }) {
                val token = call.firebasePrincipal
                val id = call.parameters.getOrFail("id")

                when (val result = questService.verifyQuest(id, token.uid)) {
                    is QuestResult.Success -> call.respond(result.data)
                    is QuestResult.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to result.message))
                    is QuestResult.Conflict -> call.respond(HttpStatusCode.Conflict, mapOf("error" to result.message))
                    is QuestResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to result.message))
                }
            }

            delete("/{id}", {
                tags = listOf("quest")
                summary = "クエスト削除"
                request {
                    pathParameter<String>("id") { description = "クエスト ID" }
                }
                response {
                    code(HttpStatusCode.NoContent) { description = "削除成功" }
                    code(HttpStatusCode.NotFound) { description = "クエスト未発見" }
                    code(HttpStatusCode.Conflict) { description = "削除不可" }
                    code(HttpStatusCode.Forbidden) { description = "作成者のみ削除可" }
                }
            }) {
                val token = call.firebasePrincipal
                val id = call.parameters.getOrFail("id")

                when (val result = questService.deleteQuest(id, token.uid)) {
                    is QuestResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is QuestResult.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to result.message))
                    is QuestResult.Conflict -> call.respond(HttpStatusCode.Conflict, mapOf("error" to result.message))
                    is QuestResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to result.message))
                }
            }

            // AI テキスト生成が利用可能かどうかを返す
            get("/ai-available", {
                tags = listOf("quest")
                summary = "AI テキスト生成の利用可否"
                response {
                    code(HttpStatusCode.OK) {
                        body<Map<String, Boolean>>()
                    }
                }
            }) {
                call.respond(mapOf("available" to (questTextGenerator != null)))
            }

            // Gemini でクエスト説明文を AI 生成する（レートリミット適用）
            rateLimit(RateLimitNames.AI_GENERATE) {
                post("/generate-text", {
                    tags = listOf("quest")
                    summary = "AI クエストテキスト生成"
                    request {
                        body<GenerateQuestTextRequest>()
                    }
                    response {
                        code(HttpStatusCode.OK) {
                            body<GenerateQuestTextResponse>()
                        }
                        code(HttpStatusCode.ServiceUnavailable) { description = "AI 未設定" }
                    }
                }) {
                    val generator =
                        questTextGenerator
                            ?: return@post call.respond(
                                HttpStatusCode.ServiceUnavailable,
                                mapOf("error" to "AI text generation is not configured"),
                            )

                    val request = call.receive<GenerateQuestTextRequest>()
                    val input =
                        QuestTextInput(
                            title = request.title,
                            description = request.description,
                            category = request.category,
                            rewardPoints = request.rewardPoints,
                            deadline = request.deadline,
                        )

                    try {
                        val result = generator.generate(input)
                        call.respond(
                            GenerateQuestTextResponse(
                                generatedTitle = result.title,
                                generatedDescription = result.description,
                            ),
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Text generation failed: ${e.message}"),
                        )
                    }
                }
            }
        }
    }
}
