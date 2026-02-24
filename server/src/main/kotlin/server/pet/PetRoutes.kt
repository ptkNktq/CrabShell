package server.pet

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.Pet
import server.auth.authenticated

fun Route.petRoutes() {
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
            call.respond(PetRepository.getPets())
        }
    }
}
