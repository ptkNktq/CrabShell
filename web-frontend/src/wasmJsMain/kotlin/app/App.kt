package app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.components.Sidebar
import core.auth.AuthRepository
import core.ui.theme.AppColorScheme
import feature.dashboard.DashboardScreen
import kotlinx.coroutines.launch

@Composable
fun App() {
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = AppColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(onSignOut = { scope.launch { AuthRepository.signOut() } })
                DashboardScreen()
            }
        }
    }
}
