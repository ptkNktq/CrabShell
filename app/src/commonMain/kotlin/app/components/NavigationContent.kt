package app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.Screen
import core.ui.components.AdminBadge

/**
 * Sidebar / DrawerContent 共通のナビゲーションコンテンツ。
 * ColumnScope 内で呼び出すこと（weight modifier を使用するため）。
 */
@Composable
fun ColumnScope.NavigationContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    version: String,
    isAdmin: Boolean,
    expanded: Boolean = true,
) {
    for (section in navigationSections) {
        if (section.label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            if (expanded) {
                Text(
                    text = section.label,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
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
            if (item.screen.adminOnly && !isAdmin) continue
            NavigationItemRow(
                icon = item.icon,
                label = item.label,
                expanded = expanded,
                selected = currentScreen == item.screen,
                onClick = { onNavigate(item.screen) },
                isAdminOnly = item.screen.adminOnly,
            )
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    for (item in bottomNavigationItems) {
        NavigationItemRow(
            icon = item.icon,
            label = item.label,
            expanded = expanded,
            selected = currentScreen == item.screen,
            onClick = { onNavigate(item.screen) },
        )
    }

    NavigationItemRow(
        icon = Icons.AutoMirrored.Filled.Logout,
        label = "ログアウト",
        expanded = expanded,
        onClick = onSignOut,
    )

    Text(
        text = version,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
fun NavigationItemRow(
    icon: ImageVector,
    label: String,
    expanded: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    isAdminOnly: Boolean = false,
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
            if (isAdminOnly) {
                Spacer(modifier = Modifier.weight(1f))
                AdminBadge()
            }
        }
    }
}
