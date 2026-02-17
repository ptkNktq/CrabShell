package core.network.di

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
import core.network.UserRepository
import core.network.UserRepositoryImpl
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
        single<UserRepository> { UserRepositoryImpl(get()) }
        single<PasskeyRepository> { PasskeyRepositoryImpl(get()) }
    }
