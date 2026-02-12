package app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.components.Sidebar
import core.auth.AuthRepository
import core.ui.theme.AppTheme
import feature.dashboard.DashboardScreen
import feature.feeding.FeedingScreen
import feature.settings.SettingsScreen
import kotlinx.coroutines.launch

enum class Screen { Dashboard, Feeding, Settings }

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            SelectionContainer {
                Row(modifier = Modifier.fillMaxSize()) {
                    Sidebar(
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it },
                        onSignOut = { scope.launch { AuthRepository.signOut() } },
                    )
                    when (currentScreen) {
                        Screen.Dashboard -> DashboardScreen()
                        Screen.Feeding -> FeedingScreen()
                        Screen.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
}
