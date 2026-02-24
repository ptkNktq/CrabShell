package server.di

import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient
import org.koin.dsl.module
import server.feeding.FeedingRepository
import server.feeding.FirestoreFeedingRepository
import server.garbage.FirestoreGarbageRepository
import server.garbage.GarbageRepository
import server.money.FirestoreMoneyRepository
import server.money.MoneyRepository
import server.pet.FirestorePetRepository
import server.pet.PetRepository
import server.quest.FirestorePointRepository
import server.quest.FirestoreQuestRepository
import server.quest.PointRepository
import server.quest.QuestRepository
import server.quest.WebhookService
import server.report.BalanceCalculationService

val serverModule =
    module {
        single<Firestore> { FirestoreClient.getFirestore() }
        single<MoneyRepository> { FirestoreMoneyRepository(get()) }
        single<QuestRepository> { FirestoreQuestRepository(get()) }
        single<PointRepository> { FirestorePointRepository(get()) }
        single<FeedingRepository> { FirestoreFeedingRepository(get()) }
        single<GarbageRepository> { FirestoreGarbageRepository(get()) }
        single<PetRepository> { FirestorePetRepository(get()) }
        single { WebhookService(get()) }
        single { BalanceCalculationService() }
    }
