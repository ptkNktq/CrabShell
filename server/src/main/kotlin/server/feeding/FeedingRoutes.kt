package server.feeding

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import model.Feeding
import model.FeedingLog
import model.FeedingSettings
import model.MealTime
import org.koin.ktor.ext.inject
import server.auth.authenticated
import server.pet.PetRepository
import server.pet.verifyPetMember
import server.util.getEnumOrFail
import java.time.Instant

fun Route.feedingRoutes() {
    val feedingRepository by inject<FeedingRepository>()
    val feedingSettingsRepository by inject<FeedingSettingsRepository>()
    val petRepository by inject<PetRepository>()

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
                val petId = call.verifyPetMember(petRepository)
                val date = call.parameters.getOrFail("date")

                call.respond(feedingRepository.getFeedingLog(petId, date))
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
                val petId = call.verifyPetMember(petRepository)
                val date = call.parameters.getOrFail("date")
                val mealTime = call.parameters.getEnumOrFail<MealTime>("mealTime")

                val timestamp = Instant.now().toString()
                feedingRepository.recordFeeding(petId, date, mealTime, timestamp)
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
                val petId = call.verifyPetMember(petRepository)
                val date = call.parameters.getOrFail("date")
                val mealTime = call.parameters.getEnumOrFail<MealTime>("mealTime")

                val body = call.receive<Map<String, String>>()
                val timestamp =
                    body["timestamp"]
                        ?: return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "timestamp is required"),
                        )

                val success = feedingRepository.updateTimestamp(petId, date, mealTime, timestamp)
                if (!success) {
                    return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Meal ${mealTime.name} is not done yet"),
                    )
                }

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
                        body<Map<String, String>>()
                    }
                }
            }) {
                val petId = call.verifyPetMember(petRepository)
                val date = call.parameters.getOrFail("date")

                val body = call.receive<Map<String, String>>()
                val note = body["note"] ?: ""

                feedingRepository.updateNote(petId, date, note)
                call.respond(mapOf("note" to note))
            }
        }
    }

    authenticated {
        get("/feeding/settings", {
            tags = listOf("feeding")
            summary = "給餌設定取得"
            response {
                code(HttpStatusCode.OK) {
                    body<FeedingSettings>()
                }
            }
        }) {
            call.respond(feedingSettingsRepository.getSettings())
        }

        put("/feeding/settings", {
            tags = listOf("feeding")
            summary = "給餌設定更新"
            request {
                body<FeedingSettings>()
            }
            response {
                code(HttpStatusCode.OK) {
                    body<FeedingSettings>()
                }
            }
        }) {
            val settings = call.receive<FeedingSettings>()
            feedingSettingsRepository.updateSettings(settings)
            call.respond(settings)
        }
    }
}
