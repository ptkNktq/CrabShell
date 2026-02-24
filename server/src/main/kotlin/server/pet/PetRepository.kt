package server.pet

import model.Pet
import server.firestore.FirestoreProvider
import server.util.await

private const val DEFAULT_PET_ID = "default-pet"
private const val DEFAULT_PET_NAME = "ぬい"

/** ペットデータの Firestore アクセスを集約するリポジトリ */
object PetRepository {
    private val firestore get() = FirestoreProvider.instance

    suspend fun getPets(): List<Pet> {
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

    /** pets コレクションにデフォルトペットが存在しなければ作成する（ブロッキング: Application 初期化時用） */
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
}
