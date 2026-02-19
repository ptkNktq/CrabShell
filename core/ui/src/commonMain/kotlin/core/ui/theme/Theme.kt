package core.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import core.ui.generated.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val regularFont = preloadFont(Res.font.notosansjp_regular, FontWeight.Normal)
    val boldFont = preloadFont(Res.font.notosansjp_bold, FontWeight.Bold)
    val fontsLoaded = regularFont.value != null && boldFont.value != null

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
