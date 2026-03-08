package app.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.Screen

@Composable
fun Sidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    version: String,
    isAdmin: Boolean = false,
    expandable: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val effectiveExpanded = expandable && expanded
    val sidebarWidth by animateDpAsState(targetValue = if (effectiveExpanded) 240.dp else 72.dp)

    Surface(
        modifier = Modifier.fillMaxHeight().width(sidebarWidth),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp)) {
            if (expandable) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "サイドバー切替",
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }

            SidebarSections(
                sections = navigationSections,
                currentScreen = currentScreen,
                expanded = effectiveExpanded,
                onNavigate = onNavigate,
            )

            if (isAdmin) {
                SidebarSections(
                    sections = listOf(adminSection),
                    currentScreen = currentScreen,
                    expanded = effectiveExpanded,
                    onNavigate = onNavigate,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            for (item in bottomNavigationItems) {
                SidebarItem(
                    icon = item.icon,
                    label = item.label,
                    expanded = effectiveExpanded,
                    selected = currentScreen == item.screen,
                    onClick = { onNavigate(item.screen) },
                )
            }

            SidebarItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "ログアウト",
                expanded = effectiveExpanded,
                onClick = onSignOut,
            )

            // バージョン表示
            Text(
                text = version,
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
private fun SidebarSections(
    sections: List<NavigationSection>,
    currentScreen: Screen,
    expanded: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    for (section in sections) {
        if (section.label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            if (expanded) {
                Text(
                    text = section.label,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }

        for (item in section.items) {
            SidebarItem(
                icon = item.icon,
                label = item.label,
                expanded = expanded,
                selected = currentScreen == item.screen,
                onClick = { onNavigate(item.screen) },
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
    val backgroundColor =
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isHovered -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
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
