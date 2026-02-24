package server.quest

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.getOrFail
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.CreateRewardRequest
import model.PointHistory
import model.Reward
import model.UserPoints
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.auth.firebasePrincipal

fun Route.pointRoutes() {
    val pointRepository by inject<PointRepository>()

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
                call.respond(pointRepository.getUserPoints(token.uid, token.name ?: ""))
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
                call.respond(pointRepository.getPointHistory(token.uid))
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
                call.respond(pointRepository.getRewards())
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
                val docId = pointRepository.createReward(rewardData)
                call.respond(
                    HttpStatusCode.Created,
                    Reward(
                        id = docId,
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
                val id = call.parameters.getOrFail("id")

                val reward = pointRepository.getReward(id)
                if (reward == null) {
                    return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Reward not found"))
                }

                val creatorUid = reward.second["creatorUid"] as? String ?: ""
                val isAdmin = token.claims["admin"] == true
                if (creatorUid != token.uid && !isAdmin) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator or admin can delete"))
                }

                pointRepository.deleteReward(id)
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
                val id = call.parameters.getOrFail("id")

                val reward = pointRepository.getReward(id)
                if (reward == null) {
                    return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Reward not found"))
                }
                val rewardData = reward.second
                val cost = (rewardData["cost"] as? Number)?.toInt() ?: 0
                val isAvailable = rewardData["isAvailable"] as? Boolean ?: true
                if (!isAvailable) {
                    return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Reward is not available"))
                }

                val rewardName = rewardData["name"] as? String ?: ""
                val success = pointRepository.exchangeReward(token.uid, token.name ?: "", cost, rewardName, id)

                if (!success) {
                    return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Insufficient points"))
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Exchanged successfully"))
            }
        }
    }
}
