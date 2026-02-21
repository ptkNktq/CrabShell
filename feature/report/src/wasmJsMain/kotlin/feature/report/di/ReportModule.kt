package feature.report.di

import feature.report.OverpaymentViewModel
import feature.report.ReportViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reportModule =
    module {
        viewModel { ReportViewModel(get()) }
        viewModel { OverpaymentViewModel(get(), get()) }
    }
