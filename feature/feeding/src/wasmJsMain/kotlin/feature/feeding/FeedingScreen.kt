package feature.feeding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import core.ui.LocalWindowSizeClass
import core.ui.util.todayDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedingScreen(vm: FeedingViewModel = koinViewModel()) {
    val today = remember { todayDate() }
    val windowSizeClass = LocalWindowSizeClass.current

    FeedingContent(
        petName = vm.uiState.pet?.name,
        selectedDate = vm.uiState.selectedDate,
        today = today,
        loading = vm.uiState.isLoading,
        error = vm.uiState.error,
        log = vm.uiState.log,
        mealOrder = vm.uiState.mealOrder,
        noteDraft = vm.uiState.noteDraft,
        editingMealTime = vm.uiState.editingMealTime,
        onDateSelected = vm::onLoadLog,
        onPreviousDay = vm::onGoToPreviousDay,
        onNextDay = vm::onGoToNextDay,
        onFeed = vm::onFeed,
        onNoteChange = vm::onUpdateNoteDraft,
        onSaveNote = vm::onSaveNote,
        onStartEditTimestamp = vm::onStartEditTimestamp,
        onCancelEditTimestamp = vm::onCancelEditTimestamp,
        onSaveTimestamp = vm::onSaveTimestamp,
        windowSizeClass = windowSizeClass,
        feedingInProgress = vm.uiState.feedingInProgress,
        isSavingNote = vm.uiState.isSavingNote,
        isSavingTimestamp = vm.uiState.isSavingTimestamp,
    )
}
