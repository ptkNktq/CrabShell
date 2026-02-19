package server.quest

import com.google.cloud.firestore.Query
import com.google.firebase.cloud.FirestoreClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import model.CreateQuestRequest
import model.Quest
import model.QuestStatus
import server.auth.FirebaseTokenKey
import server.auth.authenticated
import server.util.await
import java.time.Instant
import java.time.LocalDate

private val firestore by lazy { FirestoreClient.getFirestore() }
private val questsCollection by lazy { firestore.collection("quests") }

fun Route.questRoutes() {
    route("/quests") {
        authenticated {
            get {
                val statusFilter = call.request.queryParameters["status"]

                var query: Query = questsCollection
                if (statusFilter != null) {
                    query = query.whereEqualTo("status", statusFilter)
                }

                val docs = query.get().await().documents
                val now = LocalDate.now()
                val quests =
                    docs.map { doc ->
                        val data = doc.data
                        val status = data["status"] as? String ?: "Open"
                        val deadline = data["deadline"] as? String

                        // 期限切れチェック: Open/Accepted のクエストで期限を過ぎていたら Expired に更新
                        val effectiveStatus =
                            if (deadline != null &&
                                (status == "Open" || status == "Accepted") &&
                                LocalDate.parse(deadline).isBefore(now)
                            ) {
                                doc.reference.update("status", "Expired").await()
                                "Expired"
                            } else {
                                status
                            }

                        Quest(
                            id = doc.id,
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

            post {
                val token = call.attributes[FirebaseTokenKey]
                val request = call.receive<CreateQuestRequest>()

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

                val docRef = questsCollection.add(questData).await()
                val created =
                    Quest(
                        id = docRef.id,
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

                call.respond(HttpStatusCode.Created, created)
            }

            put("/{id}/accept") {
                val token = call.attributes[FirebaseTokenKey]
                val id =
                    call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val doc = questsCollection.document(id).get().await()
                if (!doc.exists()) {
                    return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = doc.data!!
                val status = data["status"] as? String ?: ""
                val creatorUid = data["creatorUid"] as? String ?: ""

                if (status != QuestStatus.Open.name) {
                    return@put call.respond(HttpStatusCode.Conflict, mapOf("error" to "Quest is not open"))
                }
                if (creatorUid == token.uid) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Cannot accept own quest"))
                }

                questsCollection
                    .document(id)
                    .update(
                        mapOf(
                            "status" to QuestStatus.Accepted.name,
                            "assigneeUid" to token.uid,
                            "assigneeName" to (token.name ?: ""),
                        ),
                    ).await()

                call.respond(buildQuest(id, data, QuestStatus.Accepted, token.uid, token.name))
            }

            put("/{id}/complete") {
                val token = call.attributes[FirebaseTokenKey]
                val id =
                    call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val doc = questsCollection.document(id).get().await()
                if (!doc.exists()) {
                    return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = doc.data!!
                val status = data["status"] as? String ?: ""
                val assigneeUid = data["assigneeUid"] as? String

                if (status != QuestStatus.Accepted.name) {
                    return@put call.respond(HttpStatusCode.Conflict, mapOf("error" to "Quest is not accepted"))
                }
                if (assigneeUid != token.uid) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only assignee can complete"))
                }

                val now = Instant.now().toString()
                questsCollection
                    .document(id)
                    .update(
                        mapOf(
                            "status" to QuestStatus.Completed.name,
                            "completedAt" to now,
                        ),
                    ).await()

                call.respond(
                    buildQuest(id, data, QuestStatus.Completed).copy(completedAt = now),
                )
            }

            put("/{id}/verify") {
                val token = call.attributes[FirebaseTokenKey]
                val id =
                    call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val doc = questsCollection.document(id).get().await()
                if (!doc.exists()) {
                    return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = doc.data!!
                val status = data["status"] as? String ?: ""
                val creatorUid = data["creatorUid"] as? String ?: ""

                if (status != QuestStatus.Completed.name) {
                    return@put call.respond(HttpStatusCode.Conflict, mapOf("error" to "Quest is not completed"))
                }
                if (creatorUid != token.uid) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator can verify"))
                }

                questsCollection
                    .document(id)
                    .update("status", QuestStatus.Verified.name)
                    .await()

                call.respond(buildQuest(id, data, QuestStatus.Verified))
            }

            delete("/{id}") {
                val token = call.attributes[FirebaseTokenKey]
                val id =
                    call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val doc = questsCollection.document(id).get().await()
                if (!doc.exists()) {
                    return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Quest not found"))
                }

                val data = doc.data!!
                val status = data["status"] as? String ?: ""
                val creatorUid = data["creatorUid"] as? String ?: ""

                if (status != QuestStatus.Open.name) {
                    return@delete call.respond(HttpStatusCode.Conflict, mapOf("error" to "Can only delete open quests"))
                }
                if (creatorUid != token.uid) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator can delete"))
                }

                questsCollection.document(id).delete().await()
                call.respond(HttpStatusCode.NoContent)
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
