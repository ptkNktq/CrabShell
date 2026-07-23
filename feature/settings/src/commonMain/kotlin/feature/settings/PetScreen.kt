package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.MealTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun PetScreen(modifier: Modifier = Modifier) {
    val vm: PetSettingsViewModel = koinViewModel()
    PetContent(
        state = vm.uiState,
        onPetNameChanged = vm::onPetNameChanged,
        onSavePetName = vm::onSavePetName,
        onMealOrderChanged = vm::onMealOrderChanged,
        onMealTimeChanged = vm::onMealTimeChanged,
        onSaveFeeding = vm::onSaveFeeding,
        onReminderEnabledChanged = vm::onReminderEnabledChanged,
        onReminderWebhookUrlChanged = vm::onReminderWebhookUrlChanged,
        onReminderDelayMinutesChanged = vm::onReminderDelayMinutesChanged,
        onReminderPrefixChanged = vm::onReminderPrefixChanged,
        onSaveReminder = vm::onSaveReminder,
        onTestScheduled = vm::onTestScheduled,
        onTestReminder = vm::onTestReminder,
        modifier = modifier,
    )
}

@Composable
internal fun PetContent(
    state: PetSettingsUiState,
    onPetNameChanged: (String, String) -> Unit,
    onSavePetName: (String) -> Unit,
    onMealOrderChanged: (List<MealTime>) -> Unit,
    onMealTimeChanged: (MealTime, String) -> Unit,
    onSaveFeeding: () -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderWebhookUrlChanged: (String) -> Unit,
    onReminderDelayMinutesChanged: (Int) -> Unit,
    onReminderPrefixChanged: (String) -> Unit,
    onSaveReminder: () -> Unit,
    onTestScheduled: () -> Unit,
    onTestReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PetNameCard(
            pets = state.pets,
            editingPetNames = state.editingPetNames,
            isSaving = state.petNameSaving,
            message = state.petNameMessage,
            onPetNameChanged = onPetNameChanged,
            onSavePetName = onSavePetName,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedingSettingsCard(
            mealOrder = state.mealOrder,
            mealTimes = state.mealTimes,
            isSaving = state.feedingSaving,
            message = state.feedingMessage,
            onMealOrderChanged = onMealOrderChanged,
            onMealTimeChanged = onMealTimeChanged,
            onSave = onSaveFeeding,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedingReminderCard(
            reminderEnabled = state.reminderEnabled,
            reminderWebhookUrl = state.reminderWebhookUrl,
            reminderDelayMinutes = state.reminderDelayMinutes,
            reminderPrefix = state.reminderPrefix,
            isSaving = state.reminderSaving,
            testingPhase = state.testingPhase,
            message = state.reminderMessage,
            onReminderEnabledChanged = onReminderEnabledChanged,
            onReminderWebhookUrlChanged = onReminderWebhookUrlChanged,
            onReminderDelayMinutesChanged = onReminderDelayMinutesChanged,
            onReminderPrefixChanged = onReminderPrefixChanged,
            onSave = onSaveReminder,
            onTestScheduled = onTestScheduled,
            onTestReminder = onTestReminder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
