package core.auth.di

import core.auth.AuthRepository
import core.auth.AuthRepositoryImpl
import org.koin.dsl.module

val authModule =
    module {
        single<AuthRepository> { AuthRepositoryImpl() }
    }
