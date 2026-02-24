package server.quest

import com.google.cloud.firestore.Firestore
import server.util.await

class FirestoreQuestRepository(
    private val firestore: Firestore,
) : QuestRepository {
    private val collection get() = firestore.collection("quests")

    override suspend fun getQuests(statusFilter: String?): List<Pair<String, Map<String, Any>>> {
        var query: com.google.cloud.firestore.Query = collection
        if (statusFilter != null) {
            query = query.whereEqualTo("status", statusFilter)
        }
        val docs = query.get().await().documents
        return docs.map { doc -> doc.id to doc.data }
    }

    override suspend fun getQuest(id: String): Pair<String, Map<String, Any>>? {
        val doc = collection.document(id).get().await()
        if (!doc.exists()) return null
        return doc.id to doc.data!!
    }

    override suspend fun createQuest(data: Map<String, Any?>): String {
        val docRef = collection.add(data).await()
        return docRef.id
    }

    override suspend fun updateQuest(
        id: String,
        fields: Map<String, Any?>,
    ) {
        collection.document(id).update(fields).await()
    }

    override suspend fun deleteQuest(id: String) {
        collection.document(id).delete().await()
    }

    override suspend fun countActiveQuests(): Int =
        collection
            .whereIn("status", listOf("Open", "Accepted"))
            .get()
            .await()
            .size()
}
