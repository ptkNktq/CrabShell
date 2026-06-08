package feature.settings.di

import feature.settings.CacheRefreshViewModel
import feature.settings.GarbageScheduleViewModel
import feature.settings.LicensesViewModel
import feature.settings.LoginHistoryViewModel
import feature.settings.MoneyWebhookViewModel
import feature.settings.PasskeyManagementViewModel
import feature.settings.PasswordChangeViewModel
import feature.settings.PaymentWebhookViewModel
import feature.settings.PetSettingsViewModel
import feature.settings.QuestWebhookViewModel
import feature.settings.UserNameViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule =
    module {
        viewModel { PasswordChangeViewModel(get()) }
        viewModel { PasskeyManagementViewModel(get()) }
        viewModel { LoginHistoryViewModel(get()) }
        viewModel { LicensesViewModel() }
        // Admin のみ条件付き生成
        factory { UserNameViewModel(get()) }
        factory { GarbageScheduleViewModel(get()) }
        factory { QuestWebhookViewModel(get()) }
        factory { MoneyWebhookViewModel(get()) }
        factory { PaymentWebhookViewModel(get()) }
        factory { CacheRefreshViewModel(get()) }
        factory { PetSettingsViewModel(get(), get(), get()) }
    }
