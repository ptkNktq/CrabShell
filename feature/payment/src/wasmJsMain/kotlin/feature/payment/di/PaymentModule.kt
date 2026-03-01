package feature.payment.di

import feature.payment.PaymentViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val paymentModule =
    module {
        viewModel { PaymentViewModel(get(), get(), get()) }
    }
