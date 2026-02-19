package feature.quest.di

import feature.quest.QuestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val questModule =
    module {
        viewModel { QuestViewModel(get()) }
    }
