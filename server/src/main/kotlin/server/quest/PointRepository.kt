package server.quest

import model.PointHistory
import model.Reward
import model.UserPoints

/** ポイント・報酬データのリポジトリインターフェース */
interface PointRepository {
    suspend fun getUserPoints(
        uid: String,
        displayName: String,
    ): UserPoints

    suspend fun getPointHistory(uid: String): List<PointHistory>

    suspend fun getRewards(): List<Reward>

    /** 権限チェック用に生データを返す */
    suspend fun getReward(id: String): Pair<String, Map<String, Any>>?

    suspend fun createReward(data: Map<String, Any>): String

    suspend fun deleteReward(id: String)

    /**
     * 報酬交換: トランザクションで残高チェック＋減算をアトミックに実行し、履歴を追加する。
     * @return true: 交換成功, false: ポイント不足
     */
    suspend fun exchangeReward(
        uid: String,
        displayName: String,
        cost: Int,
        rewardName: String,
        rewardId: String,
    ): Boolean

    /** クエスト達成承認時にポイントを付与する */
    suspend fun awardPoints(
        uid: String,
        displayName: String,
        points: Int,
        reason: String,
        questId: String? = null,
    )
}
