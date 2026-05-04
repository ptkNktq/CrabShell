package server.di

import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient
import com.maxmind.geoip2.DatabaseReader
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import server.cache.CacheManager
import server.cache.Cacheable
import server.config.EnvConfig
import server.feeding.FeedingReminderService
import server.feeding.FeedingRepository
import server.feeding.FeedingSettingsRepository
import server.feeding.FirestoreFeedingRepository
import server.feeding.FirestoreFeedingSettingsRepository
import server.garbage.FirestoreGarbageRepository
import server.garbage.GarbageNotificationService
import server.garbage.GarbageRepository
import server.geo.IpGeolocationService
import server.geo.MaxMindIpGeolocationService
import server.geo.NoOpIpGeolocationService
import server.loginhistory.FirestoreLoginHistoryRepository
import server.loginhistory.LoginHistoryRepository
import server.migration.FirestoreMigrations
import server.money.FirestoreMoneyRepository
import server.money.MoneyRepository
import server.money.MoneyWebhookService
import server.pet.FirestorePetRepository
import server.pet.PetRepository
import server.quest.FirestorePointRepository
import server.quest.FirestoreQuestRepository
import server.quest.PointRepository
import server.quest.QuestRepository
import server.quest.QuestService
import server.quest.QuestWebhookService
import server.report.BalanceCalculationService
import java.io.File

private val serverModuleLogger = LoggerFactory.getLogger("server.di.ServerModule")

private const val DEFAULT_GEOIP_DB_PATH = "data/GeoLite2-City.mmdb"

private fun loadGeolocationService(): IpGeolocationService {
    val path = EnvConfig["GEOIP_DB_PATH"] ?: DEFAULT_GEOIP_DB_PATH
    val file = File(path)
    if (!file.exists()) {
        serverModuleLogger.warn("GeoLite2 DB not found at '$path'; IP geolocation disabled")
        return NoOpIpGeolocationService
    }
    return runCatching {
        val reader = DatabaseReader.Builder(file).build()
        serverModuleLogger.info("GeoLite2 DB loaded from '$path'")
        MaxMindIpGeolocationService(reader)
    }.getOrElse { e ->
        serverModuleLogger.warn("Failed to load GeoLite2 DB at '$path': ${e.message}; IP geolocation disabled")
        NoOpIpGeolocationService
    }
}

val serverModule =
    module {
        single<Firestore> { FirestoreClient.getFirestore() }
        // GeoLite2 DB のロード状態を起動時ログに出すため createdAtStart で eager 初期化する。
        // 遅延評価だと初回ログイン時まで「DB が読めているか / NoOp に落ちているか」が分からない。
        single<IpGeolocationService>(createdAtStart = true) { loadGeolocationService() }
        single<MoneyRepository> { FirestoreMoneyRepository(get()) }
        single<QuestRepository> { FirestoreQuestRepository(get()) }
        single<PointRepository> { FirestorePointRepository(get()) }
        single<FeedingRepository> { FirestoreFeedingRepository(get()) }
        single<FeedingSettingsRepository> { FirestoreFeedingSettingsRepository(get()) }
        single<GarbageRepository> { FirestoreGarbageRepository(get()) }
        single<LoginHistoryRepository> { FirestoreLoginHistoryRepository(get()) }
        single<PetRepository> { FirestorePetRepository(get()) }
        single { QuestWebhookService(get()) }
        single { MoneyWebhookService(get()) }
        single { QuestService(get(), get(), get()) }
        single { FeedingReminderService(get(), get(), get()) }
        single { GarbageNotificationService(get()) }
        single { BalanceCalculationService() }
        single { FirestoreMigrations(get()) }
        single {
            CacheManager(
                listOf(
                    get<PetRepository>() as Cacheable,
                    get<GarbageRepository>() as Cacheable,
                    get<FeedingRepository>() as Cacheable,
                    get<MoneyRepository>() as Cacheable,
                ),
            )
        }
    }
