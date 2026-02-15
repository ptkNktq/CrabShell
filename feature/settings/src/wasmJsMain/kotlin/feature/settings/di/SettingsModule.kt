package feature.settings.di

import core.auth.AuthRepository
import core.network.GarbageScheduleRepository
import core.network.UserRepository
import feature.settings.GarbageScheduleViewModel
import feature.settings.PasswordChangeViewModel
import feature.settings.UserNameViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val settingsModule =
    module {
        factory { params ->
            PasswordChangeViewModel(
                params.get<CoroutineScope>(),
                get<AuthRepository>(),
            )
        }
        factory { params ->
            UserNameViewModel(
                params.get<CoroutineScope>(),
                get<UserRepository>(),
            )
        }
        factory { params ->
            GarbageScheduleViewModel(
                params.get<CoroutineScope>(),
                get<GarbageScheduleRepository>(),
            )
        }
    }
