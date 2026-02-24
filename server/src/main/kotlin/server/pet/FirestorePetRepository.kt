package server.pet

import com.google.cloud.firestore.Firestore
import model.Pet
import server.util.await
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_PET_ID = "default-pet"
private const val DEFAULT_PET_NAME = "ぬい"

class FirestorePetRepository(
    private val firestore: Firestore,
) : PetRepository {
    private val cache = ConcurrentHashMap<String, List<Pet>>()

    override suspend fun getPets(): List<Pet> {
        cache["all"]?.let { return it }

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
        cache["all"] = pets
        return pets
    }

    override fun seedDefaultPet() {
        val docRef = firestore.collection("pets").document(DEFAULT_PET_ID)
        val doc = docRef.get().get()
        if (!doc.exists()) {
            docRef
                .set(
                    mapOf("name" to DEFAULT_PET_NAME, "members" to emptyList<String>()),
                ).get()
            cache.remove("all")
        }
    }
}
