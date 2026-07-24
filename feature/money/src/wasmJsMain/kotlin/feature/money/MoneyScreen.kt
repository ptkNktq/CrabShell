package feature.money

import androidx.compose.runtime.Composable
import core.ui.LocalWindowSizeClass
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MoneyScreen(vm: MoneyViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current

    MoneyContent(
        monthlyMoney = vm.uiState.monthlyMoney,
        currentYearMonth = vm.uiState.currentYearMonth,
        loading = vm.uiState.isLoading,
        saving = vm.uiState.isSaving,
        statusSaving = vm.uiState.isStatusSaving,
        error = vm.uiState.error,
        users = vm.uiState.users,
        editingItem = vm.uiState.editingItem,
        formKey = vm.uiState.formKey,
        onPreviousMonth = vm::onGoToPreviousMonth,
        onNextMonth = vm::onGoToNextMonth,
        onEditItem = vm::onEditItem,
        onClearForm = vm::onClearForm,
        onDeleteItem = vm::onDeleteItem,
        onMoveItem = vm::onMoveItem,
        onSaveItem = vm::onSaveItem,
        onUpdateStatus = vm::onUpdateStatus,
        onImportRecurringItems = vm::onImportRecurringItems,
        windowSizeClass = windowSizeClass,
    )
}
