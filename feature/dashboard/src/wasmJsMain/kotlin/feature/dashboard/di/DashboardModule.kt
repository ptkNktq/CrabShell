package feature.dashboard.di

import feature.dashboard.DashboardViewModel
import org.koin.dsl.module

val dashboardModule =
    module {
        factory { params -> DashboardViewModel(params.get(), get(), get(), get()) }
    }
