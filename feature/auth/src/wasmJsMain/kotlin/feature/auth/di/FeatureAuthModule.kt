package feature.auth.di

import core.common.ApplicationScope
import feature.auth.LoginViewModel
import feature.auth.PasskeySetupViewModel
import feature.auth.SignInWithHistoryService
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureAuthModule =
    module {
        // 画面（ViewModel）のライフサイクルを超えてサインイン・ログイン履歴記録を完走させるため、
        // アプリ全体で 1 つの ApplicationScope を注入する
        single { SignInWithHistoryService(get(), get(), get<ApplicationScope>()) }
        viewModel { LoginViewModel(get(), get(), get(), get()) }
        viewModel { PasskeySetupViewModel(get()) }
    }
