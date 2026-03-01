package core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import core.ui.generated.Res
import core.ui.generated.notosansjp_bold
import core.ui.generated.notosansjp_regular
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun rememberFontsLoaded(): Boolean {
    val regularFont = preloadFont(Res.font.notosansjp_regular, FontWeight.Normal)
    val boldFont = preloadFont(Res.font.notosansjp_bold, FontWeight.Bold)
    return regularFont.value != null && boldFont.value != null
}
