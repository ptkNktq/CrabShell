package server.pet

import com.google.cloud.firestore.Firestore
import model.Pet
import server.cache.Cacheable
import server.util.await
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_PET_ID = "default-pet"
private const val DEFAULT_PET_NAME = "ぬい"

class FirestorePetRepository(
    private val firestore: Firestore,
) : PetRepository,
    Cacheable {
    override val cacheName: String = "pet"

    override fun clearCache() {
        cache.clear()
        membersCache.clear()
    }

    private val cache = ConcurrentHashMap<String, List<Pet>>()
    private val membersCache = ConcurrentHashMap<String, List<String>>()

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

    @Suppress("UNCHECKED_CAST")
    override suspend fun isMember(
        petId: String,
        uid: String,
    ): Boolean {
        val members =
            membersCache.getOrPut(petId) {
                val doc =
                    firestore
                        .collection("pets")
                        .document(petId)
                        .get()
                        .await()
                if (!doc.exists()) return false
                (doc.get("members") as? List<String>) ?: emptyList()
            }
        return uid in members
    }

    override suspend fun updatePetName(
        petId: String,
        name: String,
    ) {
        firestore
            .collection("pets")
            .document(petId)
            .update("name", name)
            .await()
        cache.remove("all")
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
