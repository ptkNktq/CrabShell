package core.ui.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.ui.graphics.vector.ImageVector
import model.MonthlyMoneyStatus

val MonthlyMoneyStatus.displayName: String
    get() =
        when (this) {
            MonthlyMoneyStatus.PENDING -> "確定前"
            MonthlyMoneyStatus.CONFIRMED -> "確定済み"
            MonthlyMoneyStatus.FROZEN -> "凍結"
        }

val MonthlyMoneyStatus.icon: ImageVector
    get() =
        when (this) {
            MonthlyMoneyStatus.PENDING -> Icons.Default.PendingActions
            MonthlyMoneyStatus.CONFIRMED -> Icons.Default.CheckCircle
            MonthlyMoneyStatus.FROZEN -> Icons.Default.Block
        }
