package feature.auth.di

import feature.auth.LoginViewModel
import org.koin.dsl.module

val featureAuthModule =
    module {
        factory { LoginViewModel(get()) }
    }
