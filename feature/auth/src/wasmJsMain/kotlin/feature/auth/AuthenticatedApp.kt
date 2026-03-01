package feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.theme.AppColorScheme
import kotlinx.browser.window
import org.koin.compose.koinInject

@Composable
fun AuthenticatedApp(authenticatedContent: @Composable () -> Unit) {
    val authStateHolder = koinInject<AuthStateHolder>()
    AuthenticatedAppContent(
        authState = authStateHolder.state,
        authenticatedContent = authenticatedContent,
    )
}

@Composable
internal fun AuthenticatedAppContent(
    authState: AuthState,
    authenticatedContent: @Composable () -> Unit,
) {
    when (authState) {
        is AuthState.Loading -> {
            MaterialTheme(colorScheme = AppColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        is AuthState.Unauthenticated -> {
            LoginScreen()
        }
        is AuthState.Authenticated -> {
            val authStateHolder = koinInject<AuthStateHolder>()
            var passkeySetupDone by remember {
                mutableStateOf(
                    authStateHolder.signedInViaPasskey ||
                        window.localStorage.getItem("passkey_registered") == "true",
                )
            }
            if (passkeySetupDone) {
                authenticatedContent()
            } else {
                PasskeySetupScreen(onSetupComplete = { passkeySetupDone = true })
            }
        }
    }
}
