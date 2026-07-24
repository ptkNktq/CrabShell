package feature.quest

import androidx.compose.runtime.Composable
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun QuestScreen(vm: QuestViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current
    val authStateHolder = koinInject<AuthStateHolder>()
    val currentUserUid = authStateHolder.currentUser?.uid ?: ""
    val isAdmin = authStateHolder.isAdmin

    QuestBoardContent(
        quests = vm.uiState.quests,
        isLoading = vm.uiState.isLoading,
        error = vm.uiState.error,
        isCreating = vm.uiState.isCreating,
        canCreateQuest = vm.uiState.canCreateQuest,
        isAiAvailable = vm.uiState.isAiAvailable,
        isGenerating = vm.uiState.isGenerating,
        currentUserUid = currentUserUid,
        currentTab = vm.uiState.currentTab,
        myPoints = vm.uiState.myPoints,
        rewards = vm.uiState.rewards,
        history = vm.uiState.history,
        isAdmin = isAdmin,
        isCreatingReward = vm.uiState.isCreatingReward,
        onSelectTab = vm::onSelectTab,
        onToggleCreateForm = vm::onToggleCreateForm,
        onCreateQuest = vm::onCreateQuest,
        onGenerateText = vm::onGenerateText,
        onAcceptQuest = vm::onAcceptQuest,
        onVerifyQuest = vm::onVerifyQuest,
        onDeleteQuest = vm::onDeleteQuest,
        onExchangeReward = vm::onExchangeReward,
        onToggleCreateReward = vm::onToggleCreateReward,
        onCreateReward = vm::onCreateReward,
        onDeleteReward = vm::onDeleteReward,
        onDismissError = vm::onDismissError,
        windowSizeClass = windowSizeClass,
    )
}
