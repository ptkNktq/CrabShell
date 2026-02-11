package app.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun Sidebar(onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(targetValue = if (expanded) 240.dp else 72.dp)

    Surface(
        modifier = Modifier.fillMaxHeight().width(sidebarWidth),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp)) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle sidebar",
                )
            }

            SidebarItem(Icons.Default.Home, "Dashboard", expanded)
            SidebarItem(Icons.Default.Search, "Search", expanded)
            SidebarItem(Icons.Default.Notifications, "Notifications", expanded)
            SidebarItem(Icons.Default.Settings, "Settings", expanded)
            SidebarItem(Icons.Default.Person, "Profile", expanded)

            Spacer(modifier = Modifier.weight(1f))

            // ログアウトボタン
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Sign out",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                if (expanded) {
                    Text(
                        text = "Sign Out",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, expanded: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor = if (isHovered) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(backgroundColor, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interactionSource, indication = null) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        if (expanded) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
