package feature.report

import androidx.compose.runtime.Composable
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OverpaymentScreen(vm: OverpaymentViewModel = koinViewModel()) {
    val isCompact = LocalWindowSizeClass.current == WindowSizeClass.Compact

    OverpaymentContent(
        balances = vm.uiState.balances,
        period = vm.uiState.period,
        isLoading = vm.uiState.isLoading,
        onRefresh = vm::loadBalances,
        isCompact = isCompact,
        redemptionForm = vm.uiState.redemptionForm,
        onSelectUser = vm::onSelectUser,
        onAmountChange = vm::onRedemptionAmountChange,
        onNoteChange = vm::onRedemptionNoteChange,
        onMonthPrevious = vm::onRedemptionMonthPrevious,
        onMonthNext = vm::onRedemptionMonthNext,
        onFillRemaining = vm::onFillRemainingAmount,
        onClear = vm::onClearForm,
        onConfirm = vm::onConfirmRedemption,
    )
}
