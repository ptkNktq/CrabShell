package feature.payment.di

import feature.payment.PaymentViewModel
import org.koin.dsl.module

val paymentModule =
    module {
        factory { PaymentViewModel(get()) }
    }
