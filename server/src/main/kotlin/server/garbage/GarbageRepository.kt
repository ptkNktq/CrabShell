package server.garbage

import model.GarbageTypeSchedule

/** ゴミ出しスケジュールのリポジトリインターフェース */
interface GarbageRepository {
    suspend fun getSchedules(): List<GarbageTypeSchedule>

    suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>)
}
