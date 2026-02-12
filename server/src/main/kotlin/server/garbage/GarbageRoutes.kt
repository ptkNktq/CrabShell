package server.garbage

import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule
import server.auth.authenticated

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val SETTINGS_COLLECTION = "settings"
private const val GARBAGE_DOC = "garbage_schedule"
private const val ENTRIES_FIELD = "entries"

fun Route.garbageRoutes() {
    route("/garbage/schedule") {
        authenticated {
            get {
                val doc = firestore.collection(SETTINGS_COLLECTION)
                    .document(GARBAGE_DOC).get().get()

                if (!doc.exists()) {
                    call.respond(emptyList<GarbageTypeSchedule>())
                    return@get
                }

                @Suppress("UNCHECKED_CAST")
                val entriesRaw = doc.get(ENTRIES_FIELD) as? List<Map<String, Any?>> ?: emptyList()
                val schedules = entriesRaw.map { entry ->
                    GarbageTypeSchedule(
                        garbageType = GarbageType.valueOf(entry["garbageType"] as String),
                        daysOfWeek = (entry["daysOfWeek"] as List<*>).map { (it as Long).toInt() },
                        frequency = CollectionFrequency.valueOf(
                            entry["frequency"] as? String ?: "WEEKLY"
                        ),
                    )
                }
                call.respond(schedules)
            }

            put {
                val schedules = call.receive<List<GarbageTypeSchedule>>()

                val entries = schedules.map { schedule ->
                    mapOf(
                        "garbageType" to schedule.garbageType.name,
                        "daysOfWeek" to schedule.daysOfWeek,
                        "frequency" to schedule.frequency.name,
                    )
                }

                firestore.collection(SETTINGS_COLLECTION)
                    .document(GARBAGE_DOC)
                    .set(mapOf(ENTRIES_FIELD to entries))
                    .get()

                call.respond(schedules)
            }
        }
    }
}
