package feature.feeding.di

import core.network.FeedingRepository
import core.network.PetRepository
import feature.feeding.FeedingViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val feedingModule =
    module {
        factory { params ->
            FeedingViewModel(
                params.get<CoroutineScope>(),
                get<PetRepository>(),
                get<FeedingRepository>(),
            )
        }
    }
