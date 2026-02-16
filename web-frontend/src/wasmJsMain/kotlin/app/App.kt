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
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import feature.dashboard.DashboardScreen
import feature.feeding.FeedingScreen
import feature.money.MoneyScreen
import feature.payment.PaymentScreen
import feature.settings.SettingsScreen
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.w3c.dom.events.Event

enum class Screen(val title: String, val path: String) {
    Dashboard("ダッシュボード", "/dashboard"),
    Feeding("ごはん", "/feeding"),
    Payment("お支払い", "/payment"),
    Money("お金の管理", "/money"),
    Settings("設定", "/settings"),
    ;

    companion object {
        fun fromPath(path: String): Screen = entries.find { it.path == path } ?: Dashboard
    }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var currentScreen by remember {
        mutableStateOf(Screen.fromPath(window.location.pathname))
    }
    val authRepository = koinInject<AuthRepository>()
    val onSignOut: () -> Unit = { scope.launch { authRepository.signOut() } }
    val isAdmin = (AuthStateHolder.state as? AuthState.Authenticated)?.user?.isAdmin == true

    // popstate（戻る/進む）を監視
    // active フラグで、composition 離脱後に残存するリスナーを無効化する
    DisposableEffect(Unit) {
        var active = true
        val listener: (Event) -> Unit = {
            if (active) {
                currentScreen = Screen.fromPath(window.location.pathname)
            }
        }
        window.addEventListener("popstate", listener)
        onDispose {
            active = false
            window.removeEventListener("popstate", listener)
        }
    }

    val onNavigate: (Screen) -> Unit = { screen ->
        if (screen != currentScreen) {
            window.history.pushState(null, "", screen.path)
            currentScreen = screen
        }
    }

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
private fun ScreenContent(currentScreen: Screen) {
    when (currentScreen) {
        Screen.Dashboard -> DashboardScreen()
        Screen.Feeding -> FeedingScreen()
        Screen.Payment -> PaymentScreen()
        Screen.Money -> MoneyScreen()
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
            isAdmin = isAdmin,
        )
        ScreenContent(currentScreen)
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
            isAdmin = isAdmin,
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
                ScreenContent(currentScreen)
            }
        }
    }
}
