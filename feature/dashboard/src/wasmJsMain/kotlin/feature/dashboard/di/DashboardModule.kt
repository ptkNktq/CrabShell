package feature.dashboard.di

import core.network.FeedingRepository
import core.network.GarbageScheduleRepository
import core.network.PetRepository
import feature.dashboard.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val dashboardModule =
    module {
        factory { params ->
            DashboardViewModel(
                params.get<CoroutineScope>(),
                get<PetRepository>(),
                get<FeedingRepository>(),
                get<GarbageScheduleRepository>(),
            )
        }
    }
