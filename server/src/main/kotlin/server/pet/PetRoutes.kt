package server.pet

import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.Pet
import server.auth.authenticated
import server.util.await

private val firestore by lazy { FirestoreClient.getFirestore() }

private const val DEFAULT_PET_ID = "default-pet"
private const val DEFAULT_PET_NAME = "ぬい"

/** pets コレクションにデフォルトペットが存在しなければ作成する */
fun seedDefaultPet() {
    val docRef = firestore.collection("pets").document(DEFAULT_PET_ID)
    val doc = docRef.get().get()
    if (!doc.exists()) {
        docRef
            .set(
                mapOf("name" to DEFAULT_PET_NAME, "members" to emptyList<String>()),
            ).get()
    }
}

fun Route.petRoutes() {
    authenticated {
        get("/pets") {
            val docs =
                firestore
                    .collection("pets")
                    .get()
                    .await()
                    .documents
            val pets =
                docs.map { doc ->
                    Pet(id = doc.id, name = doc.getString("name") ?: "")
                }
            call.respond(pets)
        }
    }
}
