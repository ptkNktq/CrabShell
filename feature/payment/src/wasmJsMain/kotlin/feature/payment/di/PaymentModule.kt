package feature.payment.di

import feature.payment.PaymentViewModel
import org.koin.dsl.module

val paymentModule =
    module {
        factory { params -> PaymentViewModel(params.get(), get()) }
    }
