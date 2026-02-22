package server.feeding

import com.google.cloud.firestore.SetOptions
import com.google.firebase.cloud.FirestoreClient
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.Feeding
import model.FeedingLog
import model.MealTime
import server.auth.authenticated
import server.util.await
import java.time.Instant

private val firestore by lazy { FirestoreClient.getFirestore() }

fun Route.feedingRoutes() {
    route("/pets/{petId}/feeding") {
        authenticated {
            get("/{date}", {
                tags = listOf("feeding")
                summary = "給餌ログ取得"
                request {
                    pathParameter<String>("petId") { description = "ペット ID" }
                    pathParameter<String>("date") { description = "日付（YYYY-MM-DD）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<FeedingLog>()
                    }
                }
            }) {
                val petId =
                    call.parameters["petId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date =
                    call.parameters["date"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))

                val doc =
                    firestore
                        .collection("pets")
                        .document(petId)
                        .collection("feeding_logs")
                        .document(date)
                        .get()
                        .await()

                if (!doc.exists()) {
                    call.respond(FeedingLog(date = date))
                    return@get
                }

                val data = doc.data!!

                @Suppress("UNCHECKED_CAST")
                val feedingsRaw = data["feedings"] as? Map<String, Map<String, Any?>> ?: emptyMap()
                val feedings =
                    MealTime.entries.associateWith { meal ->
                        val entry = feedingsRaw[meal.name.lowercase()]
                        if (entry != null) {
                            Feeding(
                                done = entry["done"] as? Boolean ?: false,
                                timestamp = entry["timestamp"] as? String,
                            )
                        } else {
                            Feeding()
                        }
                    }

                call.respond(
                    FeedingLog(
                        date = date,
                        note = data["note"] as? String ?: "",
                        feedings = feedings,
                    ),
                )
            }

            put("/{date}/{mealTime}", {
                tags = listOf("feeding")
                summary = "給餌記録"
                request {
                    pathParameter<String>("petId") { description = "ペット ID" }
                    pathParameter<String>("date") { description = "日付（YYYY-MM-DD）" }
                    pathParameter<String>("mealTime") { description = "食事時間（MORNING/NOON/EVENING）" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<Feeding>()
                    }
                }
            }) {
                val petId =
                    call.parameters["petId"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date =
                    call.parameters["date"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))
                val mealTimeStr =
                    call.parameters["mealTime"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "mealTime is required"))

                val mealTime =
                    try {
                        MealTime.valueOf(mealTimeStr.uppercase())
                    } catch (_: IllegalArgumentException) {
                        return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid mealTime: $mealTimeStr"))
                    }

                val timestamp = Instant.now().toString()
                val docRef =
                    firestore
                        .collection("pets")
                        .document(petId)
                        .collection("feeding_logs")
                        .document(date)

                docRef
                    .set(
                        mapOf(
                            "date" to date,
                            "feedings" to
                                mapOf(
                                    mealTime.name.lowercase() to
                                        mapOf(
                                            "done" to true,
                                            "timestamp" to timestamp,
                                        ),
                                ),
                        ),
                        SetOptions.mergeFields("date", "feedings.${mealTime.name.lowercase()}"),
                    ).await()

                call.respond(Feeding(done = true, timestamp = timestamp))
            }

            patch("/{date}/{mealTime}/timestamp", {
                tags = listOf("feeding")
                summary = "給餌タイムスタンプ更新"
                request {
                    pathParameter<String>("petId") { description = "ペット ID" }
                    pathParameter<String>("date") { description = "日付（YYYY-MM-DD）" }
                    pathParameter<String>("mealTime") { description = "食事時間（MORNING/NOON/EVENING）" }
                    body<Map<String, String>> { description = "timestamp フィールド" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        body<Feeding>()
                    }
                }
            }) {
                val petId =
                    call.parameters["petId"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date =
                    call.parameters["date"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))
                val mealTimeStr =
                    call.parameters["mealTime"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "mealTime is required"))

                val mealTime =
                    try {
                        MealTime.valueOf(mealTimeStr.uppercase())
                    } catch (_: IllegalArgumentException) {
                        return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid mealTime: $mealTimeStr"),
                        )
                    }

                val body = call.receive<Map<String, String>>()
                val timestamp =
                    body["timestamp"]
                        ?: return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "timestamp is required"),
                        )

                // 対象の meal が done=true か確認
                val docRef =
                    firestore
                        .collection("pets")
                        .document(petId)
                        .collection("feeding_logs")
                        .document(date)
                val doc = docRef.get().await()
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val feedingsRaw = doc.data?.get("feedings") as? Map<String, Map<String, Any?>>
                    val entry = feedingsRaw?.get(mealTime.name.lowercase())
                    val done = entry?.get("done") as? Boolean ?: false
                    if (!done) {
                        return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Meal ${mealTime.name} is not done yet"),
                        )
                    }
                } else {
                    return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Meal ${mealTime.name} is not done yet"),
                    )
                }

                // timestamp のみ更新
                docRef
                    .set(
                        mapOf(
                            "feedings" to
                                mapOf(
                                    mealTime.name.lowercase() to
                                        mapOf("timestamp" to timestamp),
                                ),
                        ),
                        SetOptions.mergeFields("feedings.${mealTime.name.lowercase()}.timestamp"),
                    ).await()

                call.respond(Feeding(done = true, timestamp = timestamp))
            }

            put("/{date}/note", {
                tags = listOf("feeding")
                summary = "給餌ノート更新"
                request {
                    pathParameter<String>("petId") { description = "ペット ID" }
                    pathParameter<String>("date") { description = "日付（YYYY-MM-DD）" }
                    body<Map<String, String>> { description = "note フィールド" }
                }
                response {
                    code(HttpStatusCode.OK) {
                        description = "更新後のノート"
                    }
                }
            }) {
                val petId =
                    call.parameters["petId"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date =
                    call.parameters["date"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))

                val body = call.receive<Map<String, String>>()
                val note = body["note"] ?: ""

                val docRef =
                    firestore
                        .collection("pets")
                        .document(petId)
                        .collection("feeding_logs")
                        .document(date)

                docRef
                    .set(
                        mapOf("date" to date, "note" to note),
                        SetOptions.mergeFields("date", "note"),
                    ).await()

                call.respond(mapOf("note" to note))
            }
        }
    }
}
