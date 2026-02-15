package feature.settings.di

import feature.settings.GarbageScheduleViewModel
import feature.settings.PasswordChangeViewModel
import feature.settings.UserNameViewModel
import org.koin.dsl.module

val settingsModule =
    module {
        factory { PasswordChangeViewModel(get()) }
        factory { UserNameViewModel(get()) }
        factory { GarbageScheduleViewModel(get()) }
    }
