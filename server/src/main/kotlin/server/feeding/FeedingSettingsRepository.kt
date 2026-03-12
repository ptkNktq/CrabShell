package server.feeding

import model.FeedingSettings

interface FeedingSettingsRepository {
    suspend fun getSettings(): FeedingSettings

    suspend fun updateSettings(settings: FeedingSettings)
}
