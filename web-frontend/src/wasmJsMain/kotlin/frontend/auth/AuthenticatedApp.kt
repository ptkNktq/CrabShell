package frontend.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import frontend.App

// ローディング画面用カラースキーム（テーマブランチとの衝突回避）
private val LoadingColorScheme = darkColorScheme(
    primary = Color(0xFFE8844A),
    background = Color(0xFF1A1210),
    surface = Color(0xFF1A1210),
)

@Composable
fun AuthenticatedApp() {
    SelectionContainer {
        when (AuthStateHolder.state) {
            is AuthState.Loading -> {
                MaterialTheme(colorScheme = LoadingColorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                LoginScreen()
            }
            is AuthState.Authenticated -> {
                App()
            }
        }
    }
}
