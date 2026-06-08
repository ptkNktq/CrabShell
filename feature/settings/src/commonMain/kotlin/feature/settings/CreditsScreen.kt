package feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CreditsScreen(modifier: Modifier = Modifier) {
    val vm: LicensesViewModel = koinViewModel()
    CreditsCard(
        isLoading = vm.uiState.isLoading,
        libraries = vm.uiState.libraries,
        error = vm.uiState.error,
        onRetry = vm::loadLicenses,
        modifier = modifier,
    )
}
