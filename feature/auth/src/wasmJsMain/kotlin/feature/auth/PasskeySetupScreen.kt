package feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PasskeySetupScreen(
    onSetupComplete: () -> Unit,
    vm: PasskeySetupViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) {
        vm.checkStatus()
    }

    LaunchedEffect(vm.setupComplete) {
        if (vm.setupComplete) {
            onSetupComplete()
        }
    }

    PasskeySetupContent(
        isLoading = vm.uiState.isLoading,
        isRegistering = vm.uiState.isRegistering,
        errorMessage = vm.uiState.errorMessage,
        onRegister = vm::onRegisterPasskey,
        onSkip = vm::onSkip,
    )
}
