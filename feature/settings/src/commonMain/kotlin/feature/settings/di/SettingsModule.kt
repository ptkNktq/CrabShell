package feature.settings.di

import feature.settings.CacheRefreshViewModel
import feature.settings.GarbageScheduleViewModel
import feature.settings.PasskeyManagementViewModel
import feature.settings.PasswordChangeViewModel
import feature.settings.UserNameViewModel
import feature.settings.WebhookViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule =
    module {
        viewModel { PasswordChangeViewModel(get()) }
        viewModel { PasskeyManagementViewModel(get()) }
        // Admin のみ条件付き生成
        factory { UserNameViewModel(get()) }
        factory { GarbageScheduleViewModel(get()) }
        factory { WebhookViewModel(get()) }
        factory { CacheRefreshViewModel(get()) }
    }
