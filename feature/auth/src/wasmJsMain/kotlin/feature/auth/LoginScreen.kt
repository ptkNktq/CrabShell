package feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.lifecycle.viewmodel.compose.viewModel
import core.ui.theme.AppTheme
import org.koin.compose.getKoin

@Composable
fun LoginScreen() {
    val koin = getKoin()
    val vm: LoginViewModel = viewModel { koin.get() }
    AppTheme {
        LoginContent(
            email = vm.uiState.email,
            password = vm.uiState.password,
            passwordVisible = vm.uiState.isPasswordVisible,
            errorMessage = vm.uiState.errorMessage,
            isLoading = vm.uiState.isLoading,
            onEmailChanged = vm::onEmailChanged,
            onPasswordChanged = vm::onPasswordChanged,
            onTogglePasswordVisibility = vm::onTogglePasswordVisibility,
            onSignIn = vm::onSignIn,
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
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSignIn: () -> Unit,
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

                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChanged,
                        label = { Text("メールアドレス") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                    )

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

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

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
                }
            }
        }
    }
}
