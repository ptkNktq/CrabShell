package core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

    if (!fontsLoaded) return

    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography(),
        content = content,
    )
}
