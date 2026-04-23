package app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.components.DrawerContent
import app.components.Sidebar
import core.auth.AuthRepository
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import feature.dashboard.DashboardScreen
import feature.feeding.FeedingScreen
import feature.money.MoneyScreen
import feature.payment.PaymentScreen
import feature.quest.QuestScreen
import feature.report.OverpaymentScreen
import feature.report.ReportScreen
import feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val currentScreen = Navigator.currentScreen
    val authRepository = koinInject<AuthRepository>()
    val authStateHolder = koinInject<AuthStateHolder>()
    val onSignOut: () -> Unit = { scope.launch { authRepository.signOut() } }
    val isAdmin = authStateHolder.isAdmin

    val onNavigate: (Screen) -> Unit = { Navigator.navigateTo(it) }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val windowSizeClass =
                    when {
                        maxWidth < 600.dp -> WindowSizeClass.Compact
                        maxWidth < 840.dp -> WindowSizeClass.Medium
                        else -> WindowSizeClass.Expanded
                    }

                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    SelectionContainer {
                        when (windowSizeClass) {
                            WindowSizeClass.Compact ->
                                CompactLayout(
                                    currentScreen = currentScreen,
                                    onNavigate = onNavigate,
                                    onSignOut = onSignOut,
                                    isAdmin = isAdmin,
                                )

                            WindowSizeClass.Medium ->
                                MediumLayout(
                                    currentScreen = currentScreen,
                                    onNavigate = onNavigate,
                                    onSignOut = onSignOut,
                                    isAdmin = isAdmin,
                                )

                            WindowSizeClass.Expanded ->
                                ExpandedLayout(
                                    currentScreen = currentScreen,
                                    onNavigate = onNavigate,
                                    onSignOut = onSignOut,
                                    isAdmin = isAdmin,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    currentScreen: Screen,
    isAdmin: Boolean,
) {
    // 管理者専用画面に非管理者がアクセスした場合はダッシュボードにリダイレクト
    if (currentScreen.adminOnly && !isAdmin) {
        Navigator.navigateTo(Screen.Dashboard)
        return
    }
    when (currentScreen) {
        Screen.Dashboard -> DashboardScreen()
        Screen.Feeding -> FeedingScreen()
        Screen.Payment -> PaymentScreen()
        Screen.Report -> ReportScreen()
        Screen.Money -> MoneyScreen()
        Screen.Overpayment -> OverpaymentScreen()
        Screen.Quest -> QuestScreen()
        Screen.Settings -> SettingsScreen()
    }
}

@Composable
private fun ExpandedLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    isAdmin: Boolean,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            currentScreen = currentScreen,
            onNavigate = onNavigate,
            onSignOut = onSignOut,
            version = BuildConfig.VERSION,
            isAdmin = isAdmin,
        )
        ScreenContent(currentScreen, isAdmin)
    }
}

@Composable
private fun MediumLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    isAdmin: Boolean,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            currentScreen = currentScreen,
            onNavigate = onNavigate,
            onSignOut = onSignOut,
            version = BuildConfig.VERSION,
            isAdmin = isAdmin,
            expandable = false,
        )
        ScreenContent(currentScreen, isAdmin)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    isAdmin: Boolean,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentScreen = currentScreen,
                onNavigate = {
                    onNavigate(it)
                    scope.launch { drawerState.close() }
                },
                onSignOut = onSignOut,
                version = BuildConfig.VERSION,
                isAdmin = isAdmin,
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentScreen.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "メニュー")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                ScreenContent(currentScreen, isAdmin)
            }
        }
    }
}
