package feature.feeding.di

import feature.feeding.FeedingViewModel
import org.koin.dsl.module

val feedingModule =
    module {
        factory { FeedingViewModel(get(), get()) }
    }
