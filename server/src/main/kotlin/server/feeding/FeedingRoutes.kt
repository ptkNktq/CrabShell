package server.feeding

import com.google.cloud.firestore.SetOptions
import com.google.firebase.cloud.FirestoreClient
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.Feeding
import model.FeedingLog
import model.MealTime
import server.auth.authenticated
import java.time.Instant

private val firestore by lazy { FirestoreClient.getFirestore() }

fun Route.feedingRoutes() {
    route("/pets/{petId}/feeding") {
        authenticated {
            get("/{date}") {
                val petId = call.parameters["petId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date = call.parameters["date"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))

                val doc = firestore.collection("pets").document(petId)
                    .collection("feeding_logs").document(date).get().get()

                if (!doc.exists()) {
                    call.respond(FeedingLog(date = date))
                    return@get
                }

                val data = doc.data!!
                @Suppress("UNCHECKED_CAST")
                val feedingsRaw = data["feedings"] as? Map<String, Map<String, Any?>> ?: emptyMap()
                val feedings = MealTime.entries.associateWith { meal ->
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
                    )
                )
            }

            put("/{date}/{mealTime}") {
                val petId = call.parameters["petId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date = call.parameters["date"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))
                val mealTimeStr = call.parameters["mealTime"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "mealTime is required"))

                val mealTime = try {
                    MealTime.valueOf(mealTimeStr.uppercase())
                } catch (_: IllegalArgumentException) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid mealTime: $mealTimeStr"))
                }

                val timestamp = Instant.now().toString()
                val docRef = firestore.collection("pets").document(petId)
                    .collection("feeding_logs").document(date)

                docRef.set(
                    mapOf(
                        "date" to date,
                        "feedings" to mapOf(
                            mealTime.name.lowercase() to mapOf(
                                "done" to true,
                                "timestamp" to timestamp,
                            )
                        ),
                    ),
                    SetOptions.mergeFields("date", "feedings.${mealTime.name.lowercase()}"),
                ).get()

                call.respond(Feeding(done = true, timestamp = timestamp))
            }

            put("/{date}/note") {
                val petId = call.parameters["petId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "petId is required"))
                val date = call.parameters["date"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date is required"))

                val body = call.receive<Map<String, String>>()
                val note = body["note"] ?: ""

                val docRef = firestore.collection("pets").document(petId)
                    .collection("feeding_logs").document(date)

                docRef.set(
                    mapOf("date" to date, "note" to note),
                    SetOptions.mergeFields("date", "note"),
                ).get()

                call.respond(mapOf("note" to note))
            }
        }
    }
}
