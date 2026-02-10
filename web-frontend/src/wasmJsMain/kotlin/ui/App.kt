package ui

import Sidebar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch
import shared.model.DashboardItem
import shared.model.Status
import ui.features.auth.authenticatedClient

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
