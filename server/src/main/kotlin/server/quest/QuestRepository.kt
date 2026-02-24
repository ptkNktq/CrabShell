package server.quest

import server.firestore.FirestoreProvider
import server.util.await

/** クエストデータの Firestore アクセスを集約するリポジトリ */
object QuestRepository {
    private val firestore get() = FirestoreProvider.instance
    private val collection get() = firestore.collection("quests")

    /** ステータスでフィルタしてクエスト一覧を取得。生データ (id, Map) のリストを返す */
    suspend fun getQuests(statusFilter: String? = null): List<Pair<String, Map<String, Any>>> {
        var query: com.google.cloud.firestore.Query = collection
        if (statusFilter != null) {
            query = query.whereEqualTo("status", statusFilter)
        }
        val docs = query.get().await().documents
        return docs.map { doc -> doc.id to doc.data }
    }

    suspend fun getQuest(id: String): Pair<String, Map<String, Any>>? {
        val doc = collection.document(id).get().await()
        if (!doc.exists()) return null
        return doc.id to doc.data!!
    }

    suspend fun createQuest(data: Map<String, Any?>): String {
        val docRef = collection.add(data).await()
        return docRef.id
    }

    suspend fun updateQuest(
        id: String,
        fields: Map<String, Any?>,
    ) {
        collection.document(id).update(fields).await()
    }

    suspend fun deleteQuest(id: String) {
        collection.document(id).delete().await()
    }

    /** Open + Accepted のクエスト数を返す */
    suspend fun countActiveQuests(): Int =
        collection
            .whereIn("status", listOf("Open", "Accepted"))
            .get()
            .await()
            .size()
}
