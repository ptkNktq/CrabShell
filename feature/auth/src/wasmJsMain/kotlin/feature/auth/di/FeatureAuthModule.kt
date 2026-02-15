package feature.auth.di

import core.auth.AuthRepository
import feature.auth.LoginViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val featureAuthModule =
    module {
        factory { params ->
            LoginViewModel(
                params.get<CoroutineScope>(),
                get<AuthRepository>(),
            )
        }
    }
