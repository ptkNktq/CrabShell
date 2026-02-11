package feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.theme.AppColorScheme

@Composable
fun AuthenticatedApp(authenticatedContent: @Composable () -> Unit) {
    when (AuthStateHolder.state) {
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
            authenticatedContent()
        }
    }
}
