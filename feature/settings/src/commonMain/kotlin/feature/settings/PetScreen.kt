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
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun PetScreen(modifier: Modifier = Modifier) {
    val vm: PetSettingsViewModel = koinViewModel()
    val s = vm.uiState

    if (s.isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PetNameCard(
            pets = s.pets,
            editingPetNames = s.editingPetNames,
            isSaving = s.petNameSaving,
            message = s.petNameMessage,
            onPetNameChanged = vm::onPetNameChanged,
            onSavePetName = vm::onSavePetName,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedingSettingsCard(
            mealOrder = s.mealOrder,
            mealTimes = s.mealTimes,
            isSaving = s.feedingSaving,
            message = s.feedingMessage,
            onMealOrderChanged = vm::onMealOrderChanged,
            onMealTimeChanged = vm::onMealTimeChanged,
            onSave = vm::onSaveFeeding,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedingReminderCard(
            reminderEnabled = s.reminderEnabled,
            reminderWebhookUrl = s.reminderWebhookUrl,
            reminderDelayMinutes = s.reminderDelayMinutes,
            reminderPrefix = s.reminderPrefix,
            isSaving = s.reminderSaving,
            testingPhase = s.testingPhase,
            message = s.reminderMessage,
            onReminderEnabledChanged = vm::onReminderEnabledChanged,
            onReminderWebhookUrlChanged = vm::onReminderWebhookUrlChanged,
            onReminderDelayMinutesChanged = vm::onReminderDelayMinutesChanged,
            onReminderPrefixChanged = vm::onReminderPrefixChanged,
            onSave = vm::onSaveReminder,
            onTestScheduled = vm::onTestScheduled,
            onTestReminder = vm::onTestReminder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
