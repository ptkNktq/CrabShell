package feature.money.di

import core.network.MoneyRepository
import core.network.UserRepository
import feature.money.MoneyViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val moneyModule =
    module {
        factory { params ->
            MoneyViewModel(
                params.get<CoroutineScope>(),
                get<MoneyRepository>(),
                get<UserRepository>(),
            )
        }
    }
