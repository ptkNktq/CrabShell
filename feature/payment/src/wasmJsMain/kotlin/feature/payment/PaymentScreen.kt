package feature.payment

import androidx.compose.runtime.Composable
import core.ui.LocalWindowSizeClass
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PaymentScreen(vm: PaymentViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current

    PaymentContent(
        monthlyMoney = vm.uiState.monthlyMoney,
        currentYearMonth = vm.uiState.currentYearMonth,
        currentUid = vm.uiState.viewingUid,
        loading = vm.uiState.isLoading,
        saving = vm.uiState.isSaving,
        error = vm.uiState.error,
        isAdmin = vm.uiState.isAdmin,
        users = vm.uiState.users,
        isViewingOther = vm.uiState.isViewingOther,
        onPreviousMonth = vm::onGoToPreviousMonth,
        onNextMonth = vm::onGoToNextMonth,
        onConfirmPay = vm::onRecordPayment,
        onDeletePayment = vm::onDeletePayment,
        deletingPaymentId = vm.uiState.deletingPaymentId,
        onSwitchUser = vm::onSwitchUser,
        windowSizeClass = windowSizeClass,
    )
}
