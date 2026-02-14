package app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.components.DrawerContent
import app.components.Sidebar
import core.auth.AuthRepository
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import feature.dashboard.DashboardScreen
import feature.feeding.FeedingScreen
import feature.money.MoneyScreen
import feature.settings.SettingsScreen
import kotlinx.coroutines.launch

enum class Screen(val title: String) {
    Dashboard("ダッシュボード"),
    Feeding("ごはん"),
    Money("お金の管理"),
    Settings("設定"),
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    val onSignOut: () -> Unit = { scope.launch { AuthRepository.signOut() } }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val windowSizeClass = when {
                    maxWidth < 600.dp -> WindowSizeClass.Compact
                    maxWidth < 840.dp -> WindowSizeClass.Medium
                    else -> WindowSizeClass.Expanded
                }

                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    SelectionContainer {
                        when (windowSizeClass) {
                            WindowSizeClass.Compact -> CompactLayout(
                                currentScreen = currentScreen,
                                onNavigate = { currentScreen = it },
                                onSignOut = onSignOut,
                            )

                            WindowSizeClass.Medium -> MediumLayout(
                                currentScreen = currentScreen,
                                onNavigate = { currentScreen = it },
                                onSignOut = onSignOut,
                            )

                            WindowSizeClass.Expanded -> ExpandedLayout(
                                currentScreen = currentScreen,
                                onNavigate = { currentScreen = it },
                                onSignOut = onSignOut,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(currentScreen: Screen) {
    when (currentScreen) {
        Screen.Dashboard -> DashboardScreen()
        Screen.Feeding -> FeedingScreen()
        Screen.Money -> MoneyScreen()
        Screen.Settings -> SettingsScreen()
    }
}

@Composable
private fun ExpandedLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            currentScreen = currentScreen,
            onNavigate = onNavigate,
            onSignOut = onSignOut,
        )
        ScreenContent(currentScreen)
    }
}

@Composable
private fun MediumLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            currentScreen = currentScreen,
            onNavigate = onNavigate,
            onSignOut = onSignOut,
            expandable = false,
        )
        ScreenContent(currentScreen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
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
                ScreenContent(currentScreen)
            }
        }
    }
}
