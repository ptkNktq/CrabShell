package frontend.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// テーマ変更ブランチとの衝突を避けるため、ログイン画面用に独自カラースキーム定義
private val LoginColorScheme = darkColorScheme(
    primary = Color(0xFFE8844A),
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF5C3010),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFC83848),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF5C1020),
    onSecondaryContainer = Color(0xFFFFD9DC),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = Color(0xFF3D2E28),
    onSurfaceVariant = Color(0xFFD7C2BA),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFEDE0DA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun LoginScreen() {
    MaterialTheme(colorScheme = LoginColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoginCard()
            }
        }
    }
}

@Composable
private fun LoginCard() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val performSignIn = {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter email and password"
        } else if (!isLoading) {
            isLoading = true
            errorMessage = null
            scope.launch {
                val result = AuthRepository.signIn(email, password)
                isLoading = false
                if (result.isFailure) {
                    errorMessage = result.exceptionOrNull()?.message
                        ?: "Authentication failed"
                }
            }
        }
    }

    Card(
        modifier = Modifier.width(400.dp).padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "CrabShell",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Sign in to your account",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { performSignIn() },
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { performSignIn() },
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
                    Text("Sign In")
                }
            }
        }
    }
}
