package core.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fontsLoaded = rememberFontsLoaded()

    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = if (fontsLoaded) AppTypography() else MaterialTheme.typography,
    ) {
        if (fontsLoaded) {
            content()
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
internal expect fun rememberFontsLoaded(): Boolean
