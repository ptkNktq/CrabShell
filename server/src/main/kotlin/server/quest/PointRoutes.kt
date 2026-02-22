package server.quest

import com.google.firebase.cloud.FirestoreClient
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.CreateRewardRequest
import model.PointHistory
import model.Reward
import model.UserPoints
import server.auth.authenticated
import server.auth.firebasePrincipal
import server.util.await
import java.time.Instant

private val firestore by lazy { FirestoreClient.getFirestore() }

fun Route.pointRoutes() {
    route("/points") {
        authenticated {
            get({
                tags = listOf("point")
                summary = "自分のポイント残高取得"
                response {
                    code(HttpStatusCode.OK) {
                        body<UserPoints>()
                    }
                }
            }) {
                val token = call.firebasePrincipal
                val doc =
                    firestore
                        .collection("users")
                        .document(token.uid)
                        .get()
                        .await()
                if (!doc.exists()) {
                    call.respond(UserPoints(uid = token.uid, displayName = token.name ?: "", balance = 0))
                    return@get
                }
                val data = doc.data!!
                call.respond(
                    UserPoints(
                        uid = token.uid,
                        displayName = data["displayName"] as? String ?: "",
                        balance = (data["balance"] as? Number)?.toInt() ?: 0,
                    ),
                )
            }

            get("/history", {
                tags = listOf("point")
                summary = "ポイント履歴取得"
                response {
                    code(HttpStatusCode.OK) {
                        body<List<PointHistory>>()
                    }
                }
            }) {
                val token = call.firebasePrincipal
                val docs =
                    firestore
                        .collection("users")
                        .document(token.uid)
                        .collection("point_history")
                        .get()
                        .await()
                        .documents
                val history =
                    docs
                        .map { doc ->
                            val data = doc.data
                            PointHistory(
                                id = doc.id,
                                uid = data["uid"] as? String ?: "",
                                amount = (data["amount"] as? Number)?.toInt() ?: 0,
                                reason = data["reason"] as? String ?: "",
                                questId = data["questId"] as? String,
                                rewardId = data["rewardId"] as? String,
                                timestamp = data["timestamp"] as? String ?: "",
                            )
                        }.sortedByDescending { it.timestamp }
                call.respond(history)
            }
        }
    }

    route("/rewards") {
        authenticated {
            get({
                tags = listOf("point")
                summary = "報酬一覧取得"
                response {
                    code(HttpStatusCode.OK) {
                        body<List<Reward>>()
                    }
                }
            }) {
                val docs =
                    firestore
                        .collection("rewards")
                        .get()
                        .await()
                        .documents
                val rewards =
                    docs.map { doc ->
                        val data = doc.data
                        Reward(
                            id = doc.id,
                            name = data["name"] as? String ?: "",
                            description = data["description"] as? String ?: "",
                            cost = (data["cost"] as? Number)?.toInt() ?: 0,
                            isAvailable = data["isAvailable"] as? Boolean ?: true,
                            creatorUid = data["creatorUid"] as? String ?: "",
                        )
                    }
                call.respond(rewards)
            }

            post({
                tags = listOf("point")
                summary = "報酬作成"
                request {
                    body<CreateRewardRequest>()
                }
                response {
                    code(HttpStatusCode.Created) {
                        body<Reward>()
                    }
                }
            }) {
                val token = call.firebasePrincipal
                val request = call.receive<CreateRewardRequest>()
                val rewardData =
                    mapOf(
                        "name" to request.name,
                        "description" to request.description,
                        "cost" to request.cost,
                        "isAvailable" to true,
                        "creatorUid" to token.uid,
                    )
                val docRef =
                    firestore
                        .collection("rewards")
                        .add(rewardData)
                        .await()
                call.respond(
                    HttpStatusCode.Created,
                    Reward(
                        id = docRef.id,
                        name = request.name,
                        description = request.description,
                        cost = request.cost,
                        creatorUid = token.uid,
                    ),
                )
            }

            delete("/{id}", {
                tags = listOf("point")
                summary = "報酬削除"
                request {
                    pathParameter<String>("id") { description = "報酬 ID" }
                }
                response {
                    code(HttpStatusCode.NoContent) { description = "削除成功" }
                    code(HttpStatusCode.NotFound) { description = "報酬未発見" }
                    code(HttpStatusCode.Forbidden) { description = "作成者/admin のみ削除可" }
                }
            }) {
                val token = call.firebasePrincipal
                val id =
                    call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val doc =
                    firestore
                        .collection("rewards")
                        .document(id)
                        .get()
                        .await()
                if (!doc.exists()) {
                    return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Reward not found"))
                }

                val creatorUid = doc.data!!["creatorUid"] as? String ?: ""
                val isAdmin = token.claims["admin"] == true
                if (creatorUid != token.uid && !isAdmin) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator or admin can delete"))
                }

                firestore
                    .collection("rewards")
                    .document(id)
                    .delete()
                    .await()
                call.respond(HttpStatusCode.NoContent)
            }

            post("/{id}/exchange", {
                tags = listOf("point")
                summary = "報酬交換"
                request {
                    pathParameter<String>("id") { description = "報酬 ID" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<Map<String, String>>()
                    }
                    code(HttpStatusCode.NotFound) { description = "報酬未発見" }
                    code(HttpStatusCode.Conflict) { description = "ポイント不足または利用不可" }
                }
            }) {
                val token = call.firebasePrincipal
                val id =
                    call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))

                val rewardDoc =
                    firestore
                        .collection("rewards")
                        .document(id)
                        .get()
                        .await()
                if (!rewardDoc.exists()) {
                    return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Reward not found"))
                }
                val rewardData = rewardDoc.data!!
                val cost = (rewardData["cost"] as? Number)?.toInt() ?: 0
                val isAvailable = rewardData["isAvailable"] as? Boolean ?: true
                if (!isAvailable) {
                    return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Reward is not available"))
                }

                val pointsRef =
                    firestore
                        .collection("users")
                        .document(token.uid)

                // トランザクションで残高チェック＋減算をアトミックに実行（TOCTOU 防止）
                val success =
                    firestore
                        .runTransaction { tx ->
                            val pointsDoc = tx.get(pointsRef).get()
                            val currentBalance =
                                if (pointsDoc.exists()) {
                                    (pointsDoc.data!!["balance"] as? Number)?.toInt() ?: 0
                                } else {
                                    0
                                }
                            if (currentBalance < cost) {
                                false
                            } else {
                                tx.set(
                                    pointsRef,
                                    mapOf(
                                        "balance" to (currentBalance - cost),
                                        "displayName" to (token.name ?: ""),
                                    ),
                                )
                                true
                            }
                        }.await()

                if (!success) {
                    return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Insufficient points"))
                }

                // 履歴追加
                val rewardName = rewardData["name"] as? String ?: ""
                firestore
                    .collection("users")
                    .document(token.uid)
                    .collection("point_history")
                    .add(
                        mapOf(
                            "uid" to token.uid,
                            "amount" to -cost,
                            "reason" to "報酬交換: $rewardName",
                            "rewardId" to id,
                            "timestamp" to Instant.now().toString(),
                        ),
                    ).await()

                call.respond(HttpStatusCode.OK, mapOf("message" to "Exchanged successfully"))
            }
        }
    }
}

/** クエスト達成承認時にポイントを付与する（QuestRoutes から呼び出し） */
suspend fun awardPoints(
    uid: String,
    displayName: String,
    points: Int,
    reason: String,
    questId: String? = null,
) {
    val pointsRef =
        firestore
            .collection("users")
            .document(uid)

    // トランザクションで残高読み取り＋加算をアトミックに実行（TOCTOU 防止）
    firestore
        .runTransaction { tx ->
            val doc = tx.get(pointsRef).get()
            val currentBalance =
                if (doc.exists()) {
                    (doc.data!!["balance"] as? Number)?.toInt() ?: 0
                } else {
                    0
                }
            tx.set(
                pointsRef,
                mapOf(
                    "balance" to (currentBalance + points),
                    "displayName" to displayName,
                ),
            )
            null
        }.await()

    val historyData =
        mutableMapOf<String, Any>(
            "uid" to uid,
            "amount" to points,
            "reason" to reason,
            "timestamp" to Instant.now().toString(),
        )
    if (questId != null) {
        historyData["questId"] = questId
    }
    firestore
        .collection("users")
        .document(uid)
        .collection("point_history")
        .add(historyData)
        .await()
}
