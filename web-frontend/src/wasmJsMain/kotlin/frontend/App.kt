package frontend

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.ktor.client.call.*
import io.ktor.client.request.*
import frontend.auth.AuthRepository
import frontend.auth.authenticatedClient
import kotlinx.coroutines.launch
import shared.model.DashboardItem
import shared.model.Status

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(),
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

@Composable
private fun Sidebar() {
    var expanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(targetValue = if (expanded) 240.dp else 72.dp)
    val scope = rememberCoroutineScope()

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
                    .clickable { scope.launch { AuthRepository.signOut() } }
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

@Composable
private fun DashboardScreen() {
    var items by remember { mutableStateOf<List<DashboardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                items = authenticatedClient.get("/api/items").body()
                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(
            text = "CrabShell Dashboard",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items) { item ->
                        DashboardCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(item: DashboardItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(item.status)
        }
    }
}

@Composable
private fun StatusBadge(status: Status) {
    val (text, color) = when (status) {
        Status.ACTIVE -> "Active" to Color(0xFF4CAF50)
        Status.INACTIVE -> "Inactive" to Color(0xFF9E9E9E)
        Status.PENDING -> "Pending" to Color(0xFFFFC107)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
