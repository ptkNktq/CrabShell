package feature.auth.di

import core.common.AppLogger
import feature.auth.LoginViewModel
import feature.auth.PasskeySetupViewModel
import feature.auth.SignInService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureAuthModule =
    module {
        // 画面（ViewModel）のライフサイクルを超えてサインイン・ログイン履歴記録を完走させるため、
        // アプリ全体で生存するスコープを持たせたシングルトンにする。
        // 取りこぼした例外がグローバルハンドラへ抜けないよう、スコープ全体の防御線としてハンドラを付ける。
        single {
            val exceptionHandler =
                CoroutineExceptionHandler { _, e ->
                    AppLogger.e("SignInService", "Uncaught exception in sign-in scope: ${e.message}")
                }
            SignInService(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler))
        }
        viewModel { LoginViewModel(get(), get(), get(), get()) }
        viewModel { PasskeySetupViewModel(get()) }
    }
