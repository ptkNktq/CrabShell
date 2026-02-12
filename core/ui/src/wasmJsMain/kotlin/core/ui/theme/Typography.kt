package core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import core.ui.generated.*
import org.jetbrains.compose.resources.Font

val Typography.displayExLarge: TextStyle
    get() = displayLarge.copy(
        fontSize = 64.sp,
        lineHeight = 72.sp,
    )

@Composable
fun AppTypography(): Typography {
    val notoSansJp = FontFamily(
        Font(Res.font.notosansjp_regular, FontWeight.Normal),
        Font(Res.font.notosansjp_bold, FontWeight.Bold),
    )
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = notoSansJp),
        displayMedium = default.displayMedium.copy(fontFamily = notoSansJp),
        displaySmall = default.displaySmall.copy(fontFamily = notoSansJp),
        headlineLarge = default.headlineLarge.copy(fontFamily = notoSansJp),
        headlineMedium = default.headlineMedium.copy(fontFamily = notoSansJp),
        headlineSmall = default.headlineSmall.copy(fontFamily = notoSansJp),
        titleLarge = default.titleLarge.copy(fontFamily = notoSansJp),
        titleMedium = default.titleMedium.copy(fontFamily = notoSansJp),
        titleSmall = default.titleSmall.copy(fontFamily = notoSansJp),
        bodyLarge = default.bodyLarge.copy(fontFamily = notoSansJp),
        bodyMedium = default.bodyMedium.copy(fontFamily = notoSansJp),
        bodySmall = default.bodySmall.copy(fontFamily = notoSansJp),
        labelLarge = default.labelLarge.copy(fontFamily = notoSansJp),
        labelMedium = default.labelMedium.copy(fontFamily = notoSansJp),
        labelSmall = default.labelSmall.copy(fontFamily = notoSansJp),
    )
}
