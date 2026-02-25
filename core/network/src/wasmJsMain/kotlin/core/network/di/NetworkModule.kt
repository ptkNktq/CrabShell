package core.network.di

import core.network.CacheRepository
import core.network.CacheRepositoryImpl
import core.network.FeedingRepository
import core.network.FeedingRepositoryImpl
import core.network.GarbageScheduleRepository
import core.network.GarbageScheduleRepositoryImpl
import core.network.MoneyRepository
import core.network.MoneyRepositoryImpl
import core.network.PasskeyRepository
import core.network.PasskeyRepositoryImpl
import core.network.PetRepository
import core.network.PetRepositoryImpl
import core.network.PointRepository
import core.network.PointRepositoryImpl
import core.network.QuestRepository
import core.network.QuestRepositoryImpl
import core.network.ReportRepository
import core.network.ReportRepositoryImpl
import core.network.RewardRepository
import core.network.RewardRepositoryImpl
import core.network.UserRepository
import core.network.UserRepositoryImpl
import core.network.WebhookRepository
import core.network.WebhookRepositoryImpl
import core.network.createAuthenticatedClient
import io.ktor.client.*
import org.koin.dsl.module

val networkModule =
    module {
        single<HttpClient> { createAuthenticatedClient(get()) }
        single<PetRepository> { PetRepositoryImpl(get()) }
        single<FeedingRepository> { FeedingRepositoryImpl(get()) }
        single<GarbageScheduleRepository> { GarbageScheduleRepositoryImpl(get()) }
        single<MoneyRepository> { MoneyRepositoryImpl(get()) }
        single<ReportRepository> { ReportRepositoryImpl(get()) }
        single<PointRepository> { PointRepositoryImpl(get()) }
        single<QuestRepository> { QuestRepositoryImpl(get()) }
        single<RewardRepository> { RewardRepositoryImpl(get()) }
        single<UserRepository> { UserRepositoryImpl(get()) }
        single<PasskeyRepository> { PasskeyRepositoryImpl(get()) }
        single<WebhookRepository> { WebhookRepositoryImpl(get()) }
        single<CacheRepository> { CacheRepositoryImpl(get()) }
    }
