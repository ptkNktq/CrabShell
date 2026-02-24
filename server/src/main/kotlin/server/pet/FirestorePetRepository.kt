package server.pet

import com.google.cloud.firestore.Firestore
import model.Pet
import server.util.await

private const val DEFAULT_PET_ID = "default-pet"
private const val DEFAULT_PET_NAME = "ぬい"

class FirestorePetRepository(
    private val firestore: Firestore,
) : PetRepository {
    override suspend fun getPets(): List<Pet> {
        val docs =
            firestore
                .collection("pets")
                .get()
                .await()
                .documents
        return docs.map { doc ->
            Pet(id = doc.id, name = doc.getString("name") ?: "")
        }
    }

    override fun seedDefaultPet() {
        val docRef = firestore.collection("pets").document(DEFAULT_PET_ID)
        val doc = docRef.get().get()
        if (!doc.exists()) {
            docRef
                .set(
                    mapOf("name" to DEFAULT_PET_NAME, "members" to emptyList<String>()),
                ).get()
        }
    }
}
