package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.components.Sidebar
import ui.features.dashboard.DashboardScreen
import ui.theme.AppColorScheme

@Composable
fun App() {
    MaterialTheme(
        colorScheme = AppColorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar()
                DashboardScreen()
            }
        }
    }
}
