package feature.feeding.di

import feature.feeding.FeedingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val feedingModule =
    module {
        viewModel { FeedingViewModel(get(), get(), get(), get()) }
    }
