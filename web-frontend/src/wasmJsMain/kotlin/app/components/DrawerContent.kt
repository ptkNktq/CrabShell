package app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.BuildConfig
import app.Screen

@Composable
fun DrawerContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CrabShell",
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        for (item in primaryNavigationItems) {
            NavigationDrawerItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentScreen == item.screen,
                onClick = { onNavigate(item.screen) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        for (item in bottomNavigationItems) {
            NavigationDrawerItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentScreen == item.screen,
                onClick = { onNavigate(item.screen) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Logout, contentDescription = "ログアウト") },
            label = { Text("ログアウト") },
            selected = false,
            onClick = onSignOut,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Text(
            text = "v${BuildConfig.VERSION}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}
