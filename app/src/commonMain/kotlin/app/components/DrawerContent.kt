package app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.Screen

@Composable
fun DrawerContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    version: String,
    isAdmin: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(280.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            NavigationContent(
                currentScreen = currentScreen,
                onNavigate = onNavigate,
                onSignOut = onSignOut,
                version = version,
                isAdmin = isAdmin,
            )
        }
    }
}
