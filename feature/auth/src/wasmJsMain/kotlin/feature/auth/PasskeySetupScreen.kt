package feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.theme.AppTheme
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

@Composable
internal fun PasskeySetupContent(
    isLoading: Boolean,
    isRegistering: Boolean,
    errorMessage: String?,
    onRegister: () -> Unit,
    onSkip: () -> Unit,
) {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Card(
                        modifier = Modifier.width(440.dp).padding(16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )

                            Text(
                                text = "パスキーを登録",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Text(
                                text = "パスキーを登録すると、次回からパスワードなしでログインできます。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (errorMessage != null) {
                                SelectionContainer {
                                    Text(
                                        text = errorMessage,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = onRegister,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = !isRegistering,
                            ) {
                                if (isRegistering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text("パスキーを登録する")
                                }
                            }

                            TextButton(
                                onClick = onSkip,
                                enabled = !isRegistering,
                            ) {
                                Text("あとで設定する")
                            }
                        }
                    }
                }
            }
        }
    }
}
