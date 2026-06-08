package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.QuestWebhookEvent
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun QuestScreen(modifier: Modifier = Modifier) {
    val vm: QuestWebhookViewModel = koinViewModel()
    val s = vm.uiState

    QuestWebhookSettingsCard(
        isLoading = s.isLoading,
        loadError = s.loadError,
        loadErrorMessage = s.loadErrorMessage,
        url = s.url,
        enabled = s.enabled,
        events = s.events,
        isSaving = s.isSaving,
        message = s.message,
        onUrlChanged = vm::onUrlChanged,
        onEnabledChanged = vm::onEnabledChanged,
        onToggleEvent = vm::onToggleEvent,
        onSave = vm::onSave,
        onRetry = vm::loadSettings,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestWebhookSettingsCard(
    isLoading: Boolean,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    url: String,
    enabled: Boolean,
    events: List<String>,
    isSaving: Boolean,
    message: String?,
    onUrlChanged: (String) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onToggleEvent: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    WebhookSettingsCard(
        isLoading = isLoading,
        title = "Webhook 通知",
        featureEnabled = enabled,
        url = url,
        onUrlChanged = onUrlChanged,
        isSaving = isSaving,
        onEnabledChanged = onEnabledChanged,
        onSave = onSave,
        modifier = modifier,
        loadError = loadError,
        loadErrorMessage = loadErrorMessage,
        onRetry = onRetry,
        statusMessage = message,
    ) {
        Text("通知するイベント", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestWebhookEvent.all.forEach { event ->
                FilterChip(
                    selected = event in events,
                    onClick = { onToggleEvent(event) },
                    label = { Text(QuestWebhookEvent.label(event)) },
                )
            }
        }
    }
}
