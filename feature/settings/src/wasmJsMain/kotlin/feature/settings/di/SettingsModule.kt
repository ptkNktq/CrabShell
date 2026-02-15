package feature.settings.di

import feature.settings.GarbageScheduleViewModel
import feature.settings.PasswordChangeViewModel
import feature.settings.UserNameViewModel
import org.koin.dsl.module

val settingsModule =
    module {
        factory { params -> PasswordChangeViewModel(params.get(), get()) }
        factory { params -> UserNameViewModel(params.get(), get()) }
        factory { params -> GarbageScheduleViewModel(params.get(), get()) }
    }
