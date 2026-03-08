package feature.petmanagement.di

import feature.petmanagement.PetManagementViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val petManagementModule =
    module {
        viewModel { PetManagementViewModel(get(), get()) }
    }
