package feature.money.di

import feature.money.MoneyViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val moneyModule =
    module {
        viewModel { MoneyViewModel(get(), get()) }
    }
