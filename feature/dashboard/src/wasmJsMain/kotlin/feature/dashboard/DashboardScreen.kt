package feature.dashboard

import androidx.compose.runtime.Composable
import core.ui.LocalWindowSizeClass
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(vm: DashboardViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current

    DashboardContent(
        feedingLoading = vm.uiState.feedingLoading,
        feedingError = vm.uiState.feedingError,
        feedingActionError = vm.uiState.feedingActionError,
        feedingLog = vm.uiState.feedingLog,
        petName = vm.uiState.petName,
        mealOrder = vm.uiState.mealOrder,
        todayGarbageTypes = vm.uiState.todayGarbageTypes,
        garbageUpdateLabel = vm.uiState.garbageUpdateLabel,
        currentTime = vm.uiState.currentTime,
        currentYear = vm.uiState.currentYear,
        dateWithDay = vm.uiState.dateWithDay,
        onFeedClick = vm::onFeed,
        feedingInProgress = vm.uiState.feedingInProgress,
        onRefreshFeeding = vm::onRefreshFeeding,
        windowSizeClass = windowSizeClass,
    )
}
