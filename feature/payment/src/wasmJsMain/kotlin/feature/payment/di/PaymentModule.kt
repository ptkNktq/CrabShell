package feature.payment.di

import core.network.MoneyRepository
import feature.payment.PaymentViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val paymentModule =
    module {
        factory { params ->
            PaymentViewModel(
                params.get<CoroutineScope>(),
                get<MoneyRepository>(),
            )
        }
    }
