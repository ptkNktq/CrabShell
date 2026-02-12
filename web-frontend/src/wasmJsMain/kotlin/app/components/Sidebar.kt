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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.BuildConfig
import app.Screen

@Composable
fun Sidebar(currentScreen: Screen, onNavigate: (Screen) -> Unit, onSignOut: () -> Unit) {
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
                    contentDescription = "サイドバー切替",
                )
            }

            SidebarItem(
                icon = Icons.Default.Home,
                label = "ダッシュボード",
                expanded = expanded,
                selected = currentScreen == Screen.Dashboard,
                onClick = { onNavigate(Screen.Dashboard) },
            )
            SidebarItem(
                icon = Icons.Default.Pets,
                label = "ごはん",
                expanded = expanded,
                selected = currentScreen == Screen.Feeding,
                onClick = { onNavigate(Screen.Feeding) },
            )

            Spacer(modifier = Modifier.weight(1f))

            SidebarItem(
                icon = Icons.Default.Settings,
                label = "設定",
                expanded = expanded,
                selected = currentScreen == Screen.Settings,
                onClick = { onNavigate(Screen.Settings) },
            )

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
                    contentDescription = "ログアウト",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                if (expanded) {
                    Text(
                        text = "ログアウト",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }

            // バージョン表示
            Text(
                text = if (expanded) "v${BuildConfig.VERSION}" else BuildConfig.VERSION,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        isHovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(backgroundColor, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
        )
        if (expanded) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
