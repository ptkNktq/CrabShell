package core.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import model.CollectionFrequency
import model.GarbageType

val BurnableColor = Color(0xFFFF7043)
val NonBurnableColor = Color(0xFF42A5F5)
val RecyclableColor = Color(0xFF66BB6A)

val GarbageType.label: String
    get() =
        when (this) {
            GarbageType.BURNABLE -> "可燃ゴミ"
            GarbageType.NON_BURNABLE -> "不燃ゴミ"
            GarbageType.RECYCLABLE -> "資源ゴミ"
        }

val GarbageType.icon: ImageVector
    get() =
        when (this) {
            GarbageType.BURNABLE -> Icons.Default.LocalFireDepartment
            GarbageType.NON_BURNABLE -> Icons.Default.DeleteOutline
            GarbageType.RECYCLABLE -> Icons.Default.Recycling
        }

val GarbageType.color: Color
    get() =
        when (this) {
            GarbageType.BURNABLE -> BurnableColor
            GarbageType.NON_BURNABLE -> NonBurnableColor
            GarbageType.RECYCLABLE -> RecyclableColor
        }

val CollectionFrequency.label: String
    get() =
        when (this) {
            CollectionFrequency.WEEKLY -> "毎週"
            CollectionFrequency.WEEK_1_3 -> "第1・3週"
            CollectionFrequency.WEEK_2_4 -> "第2・4週"
        }
