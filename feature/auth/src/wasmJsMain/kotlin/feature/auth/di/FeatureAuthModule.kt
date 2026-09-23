package feature.auth.di

import feature.auth.LoginViewModel
import feature.auth.PasskeySetupViewModel
import feature.auth.SignInService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureAuthModule =
    module {
        // 画面（ViewModel）のライフサイクルを超えてサインイン・ログイン履歴記録を完走させるため、
        // アプリ全体で生存するスコープを持たせたシングルトンにする
        single { SignInService(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        viewModel { LoginViewModel(get(), get(), get(), get()) }
        viewModel { PasskeySetupViewModel(get()) }
    }
