package app.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.ui.graphics.vector.ImageVector
import app.Screen

data class NavigationItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

val primaryNavigationItems =
    listOf(
        NavigationItem(Screen.Dashboard, Icons.Default.Home, "ダッシュボード"),
        NavigationItem(Screen.Feeding, Icons.Default.Pets, "ごはん"),
        NavigationItem(Screen.Payment, Icons.Default.Payment, "支払い"),
        NavigationItem(Screen.Report, Icons.Default.BarChart, "家計レポート"),
        NavigationItem(Screen.Quest, Icons.Default.Stars, "クエスト"),
    )

val adminNavigationItems =
    listOf(
        NavigationItem(Screen.Money, Icons.Default.AccountBalance, "お金の管理"),
        NavigationItem(Screen.Overpayment, Icons.Default.Payments, "過払い額"),
    )

val bottomNavigationItems =
    listOf(
        NavigationItem(Screen.Settings, Icons.Default.Settings, "設定"),
    )
