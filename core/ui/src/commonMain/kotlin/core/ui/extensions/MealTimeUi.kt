package core.ui.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import model.MealTime

// Feeding ドメイン色
val MorningColor = Color(0xCDFF4E4E)
val LunchColor = Color(0xFFFBC02D)
val EveningColor = Color(0xFF5C6BC0)
val FeedingDoneColor = Color(0xFF4CAF50)

val MealTime.label: String
    get() =
        when (this) {
            MealTime.MORNING -> "朝"
            MealTime.LUNCH -> "昼"
            MealTime.EVENING -> "晩"
        }

val MealTime.icon: ImageVector
    get() =
        when (this) {
            MealTime.MORNING -> Icons.Default.WbTwilight
            MealTime.LUNCH -> Icons.Default.WbSunny
            MealTime.EVENING -> Icons.Default.Bedtime
        }

val MealTime.color: Color
    get() =
        when (this) {
            MealTime.MORNING -> MorningColor
            MealTime.LUNCH -> LunchColor
            MealTime.EVENING -> EveningColor
        }
