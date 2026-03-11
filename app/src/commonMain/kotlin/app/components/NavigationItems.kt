package app.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.Screen

data class NavigationItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

data class NavigationSection(
    val label: String? = null,
    val items: List<NavigationItem>,
)

val navigationSections =
    listOf(
        NavigationSection(
            items =
                listOf(
                    NavigationItem(Screen.Dashboard, Icons.Default.Home, "ダッシュボード"),
                ),
        ),
        NavigationSection(
            label = "ペット",
            items =
                listOf(
                    NavigationItem(Screen.Feeding, Icons.Default.Pets, "ごはん"),
                ),
        ),
        NavigationSection(
            label = "お金",
            items =
                listOf(
                    NavigationItem(Screen.Payment, Icons.Default.Payment, "支払い"),
                    NavigationItem(Screen.Report, Icons.Default.BarChart, "家計レポート"),
                    NavigationItem(Screen.Money, Icons.Default.AccountBalance, "お金の管理"),
                    NavigationItem(Screen.Overpayment, Icons.Default.Payments, "過払い額"),
                ),
        ),
        NavigationSection(
            label = "その他",
            items =
                listOf(
                    NavigationItem(Screen.Quest, Icons.Default.Stars, "クエスト"),
                ),
        ),
    )

val bottomNavigationItems =
    listOf(
        NavigationItem(Screen.Settings, Icons.Default.Settings, "設定"),
    )

@Composable
fun AdminBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "管理者",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}
