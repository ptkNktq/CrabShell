package ui

import Sidebar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ui.features.dashboard.DashboardScreen

private val CrabShellColorScheme = darkColorScheme(
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
)

@Composable
fun App() {
    MaterialTheme(
        colorScheme = CrabShellColorScheme,
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
