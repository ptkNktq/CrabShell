package feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import core.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(vm: LoginViewModel = koinViewModel()) {
    AppTheme {
        LoginContent(
            email = vm.uiState.email,
            password = vm.uiState.password,
            passwordVisible = vm.uiState.isPasswordVisible,
            errorMessage = vm.uiState.errorMessage,
            isLoading = vm.uiState.isLoading,
            loginMode = vm.uiState.loginMode,
            isWebAuthnSupported = vm.uiState.isWebAuthnSupported,
            onEmailChanged = vm::onEmailChanged,
            onPasswordChanged = vm::onPasswordChanged,
            onTogglePasswordVisibility = vm::onTogglePasswordVisibility,
            onSignIn = vm::onSignIn,
            onPasskeySignIn = vm::onPasskeySignIn,
            onSwitchToPasskey = vm::onSwitchToPasskey,
            onSwitchToEmailPassword = vm::onSwitchToEmailPassword,
        )
    }
}

@Composable
internal fun LoginContent(
    email: String,
    password: String,
    passwordVisible: Boolean,
    errorMessage: String?,
    isLoading: Boolean,
    loginMode: LoginMode,
    isWebAuthnSupported: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSignIn: () -> Unit,
    onPasskeySignIn: () -> Unit,
    onSwitchToPasskey: () -> Unit,
    onSwitchToEmailPassword: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.width(400.dp).padding(16.dp),
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
                    Text(
                        text = "Shell",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // メールアドレス入力（両モードで共通）
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChanged,
                        label = { Text("メールアドレス") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction =
                                    if (loginMode == LoginMode.PASSKEY) {
                                        ImeAction.Done
                                    } else {
                                        ImeAction.Next
                                    },
                            ),
                        keyboardActions =
                            if (loginMode == LoginMode.PASSKEY) {
                                KeyboardActions(onDone = { onPasskeySignIn() })
                            } else {
                                KeyboardActions.Default
                            },
                        modifier =
                            Modifier.fillMaxWidth().then(
                                if (loginMode == LoginMode.PASSKEY) {
                                    Modifier.onPreviewKeyEvent { event ->
                                        if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                            onPasskeySignIn()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        enabled = !isLoading,
                    )

                    when (loginMode) {
                        LoginMode.PASSKEY -> {
                            PasskeyLoginSection(
                                isLoading = isLoading,
                                onPasskeySignIn = onPasskeySignIn,
                                onSwitchToEmailPassword = onSwitchToEmailPassword,
                            )
                        }
                        LoginMode.EMAIL_PASSWORD -> {
                            EmailPasswordLoginSection(
                                password = password,
                                passwordVisible = passwordVisible,
                                isLoading = isLoading,
                                isWebAuthnSupported = isWebAuthnSupported,
                                onPasswordChanged = onPasswordChanged,
                                onTogglePasswordVisibility = onTogglePasswordVisibility,
                                onSignIn = onSignIn,
                                onSwitchToPasskey = onSwitchToPasskey,
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasskeyLoginSection(
    isLoading: Boolean,
    onPasskeySignIn: () -> Unit,
    onSwitchToEmailPassword: () -> Unit,
) {
    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onPasskeySignIn,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("パスキーでログイン")
        }
    }

    TextButton(
        onClick = onSwitchToEmailPassword,
        enabled = !isLoading,
    ) {
        Text("パスワードでログイン")
    }
}

@Composable
private fun EmailPasswordLoginSection(
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    isWebAuthnSupported: Boolean,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSignIn: () -> Unit,
    onSwitchToPasskey: () -> Unit,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChanged,
        label = { Text("パスワード") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "パスワードを隠す" else "パスワードを表示",
                )
            }
        },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSignIn() }),
        modifier =
            Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                    onSignIn()
                    true
                } else {
                    false
                }
            },
        enabled = !isLoading,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onSignIn,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("ログイン")
        }
    }

    if (isWebAuthnSupported) {
        TextButton(
            onClick = onSwitchToPasskey,
            enabled = !isLoading,
        ) {
            Text("パスキーでログイン")
        }
    }
}
