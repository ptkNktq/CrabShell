package server.quest

import model.QuestCategory

/** クエストテキスト生成のインターフェース（将来のローカル LLM 対応用） */
interface QuestTextGenerator {
    suspend fun generate(input: QuestTextInput): String
}

data class QuestTextInput(
    val title: String,
    val category: QuestCategory,
    val rewardPoints: Int,
    val deadline: String?,
)
