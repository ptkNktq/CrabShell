package core.ui.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import model.QuestCategory
import model.QuestStatus

val QuestCategory.label: String
    get() =
        when (this) {
            QuestCategory.Housework -> "家事"
            QuestCategory.Errand -> "お使い"
            QuestCategory.Cooking -> "料理"
            QuestCategory.Cleaning -> "掃除"
            QuestCategory.Pet -> "ペット"
            QuestCategory.Other -> "その他"
        }

val QuestCategory.icon: ImageVector
    get() =
        when (this) {
            QuestCategory.Housework -> Icons.Default.Home
            QuestCategory.Errand -> Icons.Default.ShoppingCart
            QuestCategory.Cooking -> Icons.Default.Restaurant
            QuestCategory.Cleaning -> Icons.Default.CleaningServices
            QuestCategory.Pet -> Icons.Default.Pets
            QuestCategory.Other -> Icons.Default.HelpOutline
        }

val QuestStatus.label: String
    get() =
        when (this) {
            QuestStatus.Open -> "公開中"
            QuestStatus.Accepted -> "受注済み"
            QuestStatus.Completed -> "達成報告済み"
            QuestStatus.Verified -> "承認済み"
            QuestStatus.Expired -> "期限切れ"
        }
