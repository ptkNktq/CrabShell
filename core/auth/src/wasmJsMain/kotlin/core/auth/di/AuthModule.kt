package core.auth.di

import core.auth.AuthRepository
import core.auth.AuthRepositoryImpl
import core.auth.AuthStateHolder
import core.common.TabResumedEvent
import org.koin.dsl.module

val authModule =
    module {
        single { AuthStateHolder() }
        single<AuthRepository> { AuthRepositoryImpl(get()) }
        single { TabResumedEvent() }
    }
