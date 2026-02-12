package core.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import model.MealTime

/** UI 上の表示順（昼→晩→朝） */
val MealTime.Companion.displayOrder: List<MealTime>
    get() = listOf(MealTime.LUNCH, MealTime.EVENING, MealTime.MORNING)

val MealTime.label: String
    get() = when (this) {
        MealTime.MORNING -> "朝"
        MealTime.LUNCH -> "昼"
        MealTime.EVENING -> "晩"
    }

val MealTime.icon: ImageVector
    get() = when (this) {
        MealTime.MORNING -> Icons.Default.WbTwilight
        MealTime.LUNCH -> Icons.Default.WbSunny
        MealTime.EVENING -> Icons.Default.Bedtime
    }

val MealTime.color: Color
    get() = when (this) {
        MealTime.MORNING -> MorningColor
        MealTime.LUNCH -> LunchColor
        MealTime.EVENING -> EveningColor
    }
