package server.di

import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient
import org.koin.dsl.module
import server.cache.CacheManager
import server.cache.Cacheable
import server.feeding.FeedingReminderService
import server.feeding.FeedingRepository
import server.feeding.FeedingSettingsRepository
import server.feeding.FirestoreFeedingRepository
import server.feeding.FirestoreFeedingSettingsRepository
import server.garbage.FirestoreGarbageRepository
import server.garbage.GarbageNotificationService
import server.garbage.GarbageRepository
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

val serverModule =
    module {
        single<Firestore> { FirestoreClient.getFirestore() }
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
