package feature.report

import model.MonthlyMoney
import model.MonthlyMoneyStatus

internal const val DEFAULT_REDEMPTION_NOTE = "過払い金から支払い"

data class RedemptionFormState(
    val selectedUid: String = "",
    val selectedYearMonth: String = "",
    val amountText: String = "",
    val noteText: String = DEFAULT_REDEMPTION_NOTE,
    val isSaving: Boolean = false,
    val error: String? = null,
    val monthData: MonthlyMoney? = null,
) {
    val isMonthFrozen: Boolean get() = monthData?.status == MonthlyMoneyStatus.FROZEN
    val canSubmit: Boolean
        get() =
            selectedUid.isNotEmpty() &&
                (amountText.toLongOrNull() ?: 0L) > 0L &&
                !isSaving &&
                !isMonthFrozen
}
