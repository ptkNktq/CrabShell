package feature.dashboard.di

import feature.dashboard.DashboardViewModel
import org.koin.dsl.module

val dashboardModule =
    module {
        factory { DashboardViewModel(get(), get(), get()) }
    }
