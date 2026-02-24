package server.quest

/** クエストデータのリポジトリインターフェース */
interface QuestRepository {
    suspend fun getQuests(statusFilter: String? = null): List<Pair<String, Map<String, Any>>>

    suspend fun getQuest(id: String): Pair<String, Map<String, Any>>?

    suspend fun createQuest(data: Map<String, Any?>): String

    suspend fun updateQuest(
        id: String,
        fields: Map<String, Any?>,
    )

    suspend fun deleteQuest(id: String)

    /** Open + Accepted のクエスト数を返す */
    suspend fun countActiveQuests(): Int
}
