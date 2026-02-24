package server.quest

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.CreateQuestRequest
import model.GenerateQuestTextRequest
import model.GenerateQuestTextResponse
import model.Quest
import model.QuestStatus
import model.WebhookEvent
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.config.EnvConfig
import java.time.Instant
import java.time.LocalDate

private const val MAX_ACTIVE_QUESTS = 10

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
    val questRepository by inject<QuestRepository>()
    val pointRepository by inject<PointRepository>()
    val webhookService by inject<WebhookService>()

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
                val rawQuests = questRepository.getQuests(statusFilter)
                val now = LocalDate.now()

                val quests =
                    rawQuests.map { (id, data) ->
                        val status = data["status"] as? String ?: "Open"
                        val deadline = data["deadline"] as? String

                        // 期限切れチェック: Open/Accepted のクエストで期限を過ぎていたら Expired に更新
                        // deadline は "YYYY-MM-DD" or "YYYY-MM-DD HH:MM" 形式
                        val effectiveStatus =
                            if (deadline != null &&
                                (status == "Open" || status == "Accepted") &&
                                LocalDate.parse(deadline.take(10)).isBefore(now)
                            ) {
                                questRepository.updateQuest(id, mapOf("status" to "Expired"))
                                "Expired"
                            } else {
                                status
                            }

                        Quest(
                            id = id,
                            title = data["title"] as? String ?: "",
                            description = data["description"] as? String ?: "",
                            category = parseCategory(data["category"] as? String),
                            rewardPoints = (data["rewardPoints"] as? Number)?.toInt() ?: 0,
                            creatorUid = data["creatorUid"] as? String ?: "",
                            creatorName = data["creatorName"] as? String ?: "",
                            assigneeUid = data["assigneeUid"] as? String,
                            assigneeName = data["assigneeName"] as? String,
                            status = QuestStatus.valueOf(effectiveStatus),
                            deadline = deadline,
                            createdAt = data["createdAt"] as? String ?: "",
                            completedAt = data["completedAt"] as? String,
                        )
                    }

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

                // 同時発行数の上限チェック（全ユーザーで最大10件）
                val activeCount = questRepository.countActiveQuests()
                if (activeCount >= MAX_ACTIVE_QUESTS) {
                    return@post call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "同時に発行できるクエストは${MAX_ACTIVE_QUESTS}件までです"),
                    )
                }

                val questData =
                    mapOf(
                        "title" to request.title,
                        "description" to request.description,
                        "category" to request.category.name,
                        "rewardPoints" to request.rewardPoints,
                        "creatorUid" to token.uid,
                        "creatorName" to (token.name ?: ""),
                        "assigneeUid" to null,
                        "assigneeName" to null,
                        "status" to QuestStatus.Open.name,
                        "deadline" to request.deadline,
                        "createdAt" to Instant.now().toString(),
                        "completedAt" to null,
                    )

                val docId = questRepository.createQuest(questData)
                val created =
                    Quest(
                        id = docId,
                        title = request.title,
                        description = request.description,
                        category = request.category,
                        rewardPoints = request.rewardPoints,
                        creatorUid = token.uid,
                        creatorName = token.name ?: "",
                        status = QuestStatus.Open,
                        deadline = request.deadline,
                        createdAt = questData["createdAt"] as String,
                    )

                webhookService.notify(WebhookEvent.QUEST_CREATED, created)
                call.respond(HttpStatusCode.Created, created)
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
                val id =
                    call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val quest = questRepository.getQuest(id)
                if (quest == null) {
                    return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = quest.second
                val status = data["status"] as? String ?: ""
                val creatorUid = data["creatorUid"] as? String ?: ""

                if (status != QuestStatus.Open.name) {
                    return@put call.respond(HttpStatusCode.Conflict, mapOf("error" to "Quest is not open"))
                }
                if (creatorUid == token.uid) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Cannot accept own quest"))
                }

                questRepository.updateQuest(
                    id,
                    mapOf(
                        "status" to QuestStatus.Accepted.name,
                        "assigneeUid" to token.uid,
                        "assigneeName" to (token.name ?: ""),
                    ),
                )

                call.respond(buildQuest(id, data, QuestStatus.Accepted, token.uid, token.name))
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
                val id =
                    call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val quest = questRepository.getQuest(id)
                if (quest == null) {
                    return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = quest.second
                val status = data["status"] as? String ?: ""
                val creatorUid = data["creatorUid"] as? String ?: ""

                if (status != QuestStatus.Accepted.name) {
                    return@put call.respond(HttpStatusCode.Conflict, mapOf("error" to "Quest is not accepted"))
                }
                if (creatorUid != token.uid) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator can verify"))
                }

                val now = Instant.now().toString()
                questRepository.updateQuest(
                    id,
                    mapOf(
                        "status" to QuestStatus.Verified.name,
                        "completedAt" to now,
                    ),
                )

                // ポイント付与
                val assigneeUid = data["assigneeUid"] as? String
                val assigneeName = data["assigneeName"] as? String ?: ""
                val rewardPoints = (data["rewardPoints"] as? Number)?.toInt() ?: 0
                val questTitle = data["title"] as? String ?: ""
                if (assigneeUid != null && rewardPoints > 0) {
                    pointRepository.awardPoints(assigneeUid, assigneeName, rewardPoints, "クエスト達成: $questTitle", questId = id)
                }

                val verifiedQuest = buildQuest(id, data, QuestStatus.Verified)
                webhookService.notify(WebhookEvent.QUEST_VERIFIED, verifiedQuest)
                call.respond(verifiedQuest)
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
                val id =
                    call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val quest = questRepository.getQuest(id)
                if (quest == null) {
                    return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = quest.second
                val status = data["status"] as? String ?: ""
                val creatorUid = data["creatorUid"] as? String ?: ""

                if (status != QuestStatus.Open.name && status != QuestStatus.Expired.name) {
                    return@delete call.respond(HttpStatusCode.Conflict, mapOf("error" to "Can only delete open or expired quests"))
                }
                if (creatorUid != token.uid) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator can delete"))
                }

                questRepository.deleteQuest(id)
                call.respond(HttpStatusCode.NoContent)
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

            // Gemini でクエスト説明文を AI 生成する
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

private fun parseCategory(value: String?): model.QuestCategory =
    try {
        model.QuestCategory.valueOf(value ?: "Other")
    } catch (_: IllegalArgumentException) {
        model.QuestCategory.Other
    }

private fun buildQuest(
    id: String,
    data: Map<String, Any>,
    statusOverride: QuestStatus,
    assigneeUidOverride: String? = null,
    assigneeNameOverride: String? = null,
): Quest =
    Quest(
        id = id,
        title = data["title"] as? String ?: "",
        description = data["description"] as? String ?: "",
        category = parseCategory(data["category"] as? String),
        rewardPoints = (data["rewardPoints"] as? Number)?.toInt() ?: 0,
        creatorUid = data["creatorUid"] as? String ?: "",
        creatorName = data["creatorName"] as? String ?: "",
        assigneeUid = assigneeUidOverride ?: data["assigneeUid"] as? String,
        assigneeName = assigneeNameOverride ?: data["assigneeName"] as? String,
        status = statusOverride,
        deadline = data["deadline"] as? String,
        createdAt = data["createdAt"] as? String ?: "",
        completedAt = data["completedAt"] as? String,
    )
