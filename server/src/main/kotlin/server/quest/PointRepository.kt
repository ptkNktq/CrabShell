package server.quest

import model.PointHistory
import model.Reward
import model.UserPoints
import server.firestore.FirestoreProvider
import server.util.await
import java.time.Instant

/** ポイント・報酬データの Firestore アクセスを集約するリポジトリ */
object PointRepository {
    private val firestore get() = FirestoreProvider.instance

    suspend fun getUserPoints(
        uid: String,
        displayName: String,
    ): UserPoints {
        val doc =
            firestore
                .collection("users")
                .document(uid)
                .get()
                .await()
        if (!doc.exists()) {
            return UserPoints(uid = uid, displayName = displayName, balance = 0)
        }
        val data = doc.data!!
        return UserPoints(
            uid = uid,
            displayName = data["displayName"] as? String ?: "",
            balance = (data["balance"] as? Number)?.toInt() ?: 0,
        )
    }

    suspend fun getPointHistory(uid: String): List<PointHistory> {
        val docs =
            firestore
                .collection("users")
                .document(uid)
                .collection("point_history")
                .get()
                .await()
                .documents
        return docs
            .map { doc ->
                val data = doc.data
                PointHistory(
                    id = doc.id,
                    uid = data["uid"] as? String ?: "",
                    amount = (data["amount"] as? Number)?.toInt() ?: 0,
                    reason = data["reason"] as? String ?: "",
                    questId = data["questId"] as? String,
                    rewardId = data["rewardId"] as? String,
                    timestamp = data["timestamp"] as? String ?: "",
                )
            }.sortedByDescending { it.timestamp }
    }

    suspend fun getRewards(): List<Reward> {
        val docs =
            firestore
                .collection("rewards")
                .get()
                .await()
                .documents
        return docs.map { doc ->
            val data = doc.data
            Reward(
                id = doc.id,
                name = data["name"] as? String ?: "",
                description = data["description"] as? String ?: "",
                cost = (data["cost"] as? Number)?.toInt() ?: 0,
                isAvailable = data["isAvailable"] as? Boolean ?: true,
                creatorUid = data["creatorUid"] as? String ?: "",
            )
        }
    }

    /** 権限チェック用に生データを返す */
    suspend fun getReward(id: String): Pair<String, Map<String, Any>>? {
        val doc =
            firestore
                .collection("rewards")
                .document(id)
                .get()
                .await()
        if (!doc.exists()) return null
        return doc.id to doc.data!!
    }

    suspend fun createReward(data: Map<String, Any>): String {
        val docRef =
            firestore
                .collection("rewards")
                .add(data)
                .await()
        return docRef.id
    }

    suspend fun deleteReward(id: String) {
        firestore
            .collection("rewards")
            .document(id)
            .delete()
            .await()
    }

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
    ): Boolean {
        val pointsRef =
            firestore
                .collection("users")
                .document(uid)

        val success =
            firestore
                .runTransaction { tx ->
                    val pointsDoc = tx.get(pointsRef).get()
                    val currentBalance =
                        if (pointsDoc.exists()) {
                            (pointsDoc.data!!["balance"] as? Number)?.toInt() ?: 0
                        } else {
                            0
                        }
                    if (currentBalance < cost) {
                        false
                    } else {
                        tx.set(
                            pointsRef,
                            mapOf(
                                "balance" to (currentBalance - cost),
                                "displayName" to displayName,
                            ),
                        )
                        true
                    }
                }.await()

        if (!success) return false

        firestore
            .collection("users")
            .document(uid)
            .collection("point_history")
            .add(
                mapOf(
                    "uid" to uid,
                    "amount" to -cost,
                    "reason" to "報酬交換: $rewardName",
                    "rewardId" to rewardId,
                    "timestamp" to Instant.now().toString(),
                ),
            ).await()

        return true
    }

    /** クエスト達成承認時にポイントを付与する */
    suspend fun awardPoints(
        uid: String,
        displayName: String,
        points: Int,
        reason: String,
        questId: String? = null,
    ) {
        val pointsRef =
            firestore
                .collection("users")
                .document(uid)

        firestore
            .runTransaction { tx ->
                val doc = tx.get(pointsRef).get()
                val currentBalance =
                    if (doc.exists()) {
                        (doc.data!!["balance"] as? Number)?.toInt() ?: 0
                    } else {
                        0
                    }
                tx.set(
                    pointsRef,
                    mapOf(
                        "balance" to (currentBalance + points),
                        "displayName" to displayName,
                    ),
                )
                null
            }.await()

        val historyData =
            mutableMapOf<String, Any>(
                "uid" to uid,
                "amount" to points,
                "reason" to reason,
                "timestamp" to Instant.now().toString(),
            )
        if (questId != null) {
            historyData["questId"] = questId
        }
        firestore
            .collection("users")
            .document(uid)
            .collection("point_history")
            .add(historyData)
            .await()
    }
}
