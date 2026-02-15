package feature.settings.di

import feature.settings.GarbageScheduleViewModel
import feature.settings.PasswordChangeViewModel
import feature.settings.UserNameViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule =
    module {
        viewModel { PasswordChangeViewModel(get()) }
        // admin 専用 VM は条件付き生成のため factory で登録
        factory { UserNameViewModel(get()) }
        factory { GarbageScheduleViewModel(get()) }
    }
