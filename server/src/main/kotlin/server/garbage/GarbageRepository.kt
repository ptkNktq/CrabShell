package server.garbage

import model.GarbageNotificationSettings
import model.GarbageTypeSchedule

/** ゴミ出しスケジュールのリポジトリインターフェース */
interface GarbageRepository {
    suspend fun getSchedules(): List<GarbageTypeSchedule>

    suspend fun saveSchedules(schedules: List<GarbageTypeSchedule>)

    suspend fun getNotificationSettings(): GarbageNotificationSettings

    suspend fun saveNotificationSettings(settings: GarbageNotificationSettings)
}
