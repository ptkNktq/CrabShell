package server.feeding

import model.FeedingLog
import model.MealTime

/** 給餌ログのリポジトリインターフェース */
interface FeedingRepository {
    suspend fun getFeedingLog(
        petId: String,
        date: String,
    ): FeedingLog

    suspend fun recordFeeding(
        petId: String,
        date: String,
        mealTime: MealTime,
        timestamp: String,
    )

    /**
     * タイムスタンプを更新する。対象の meal が done=true でない場合は false を返す。
     * @return true: 更新成功, false: 対象が done=true でない
     */
    suspend fun updateTimestamp(
        petId: String,
        date: String,
        mealTime: MealTime,
        timestamp: String,
    ): Boolean

    suspend fun updateNote(
        petId: String,
        date: String,
        note: String,
    )
}
