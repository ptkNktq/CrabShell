package feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CreditsScreen(modifier: Modifier = Modifier) {
    val vm: LicensesViewModel = koinViewModel()
    CreditsContent(
        state = vm.uiState,
        onRetry = vm::loadLicenses,
        modifier = modifier,
    )
}

@Composable
internal fun CreditsContent(
    state: LicensesUiState,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CreditsCard(
        isLoading = state.isLoading,
        libraries = state.libraries,
        error = state.error,
        onRetry = onRetry,
        modifier = modifier,
    )
}
