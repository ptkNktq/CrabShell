package feature.money.di

import feature.money.MoneyViewModel
import org.koin.dsl.module

val moneyModule =
    module {
        factory { MoneyViewModel(get(), get()) }
    }
