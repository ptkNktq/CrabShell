package feature.auth.di

import feature.auth.LoginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureAuthModule =
    module {
        viewModel { LoginViewModel(get()) }
    }
