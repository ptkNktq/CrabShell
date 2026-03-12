package server.pet

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.Pet
import org.koin.ktor.ext.inject
import server.auth.authenticated

fun Route.petRoutes() {
    val petRepository by inject<PetRepository>()

    authenticated {
        get("/pets", {
            tags = listOf("pet")
            summary = "ペット一覧取得"
            response {
                code(HttpStatusCode.OK) {
                    body<List<Pet>>()
                }
            }
        }) {
            call.respond(petRepository.getPets())
        }

        put("/pets/{petId}", {
            tags = listOf("pet")
            summary = "ペット名更新"
            request {
                pathParameter<String>("petId") { description = "ペット ID" }
                body<Map<String, String>> { description = "name フィールド" }
            }
            response {
                code(HttpStatusCode.OK) {
                    body<Pet>()
                }
            }
        }) {
            val petId = call.verifyPetMember(petRepository)
            val body = call.receive<Map<String, String>>()
            val name =
                body["name"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "name is required"),
                    )
            petRepository.updatePetName(petId, name)
            call.respond(Pet(id = petId, name = name))
        }
    }
}
