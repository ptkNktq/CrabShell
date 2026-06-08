package feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.ui.components.LoadableCardContent
import core.ui.extensions.color
import core.ui.extensions.icon
import core.ui.extensions.label
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule
import org.koin.compose.viewmodel.koinViewModel

private val dayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

@Composable
internal fun GarbageScreen(modifier: Modifier = Modifier) {
    val vm: GarbageScheduleViewModel = koinViewModel()
    val s = vm.uiState

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GarbageScheduleCard(
            isLoading = s.isLoading,
            loadError = s.loadError,
            loadErrorMessage = s.loadErrorMessage,
            schedules = s.schedules,
            garbageMessage = s.message,
            garbageSaving = s.isSaving,
            onToggleDay = vm::onToggleDay,
            onFrequencyChange = vm::onChangeFrequency,
            onSaveClick = vm::onSaveSchedule,
            onRetry = vm::loadSchedules,
            modifier = Modifier.fillMaxWidth(),
        )

        GarbageNotificationCard(
            isLoading = s.notificationLoading,
            loadError = s.notificationLoadError,
            loadErrorMessage = s.notificationLoadErrorMessage,
            enabled = s.notificationEnabled,
            webhookUrl = s.notificationWebhookUrl,
            notifyHour = s.notificationHour,
            prefix = s.notificationPrefix,
            isSaving = s.notificationSaving,
            isHourValid = s.isNotificationHourValid,
            message = s.notificationMessage,
            onEnabledChanged = vm::onNotificationEnabledChanged,
            onWebhookUrlChanged = vm::onNotificationWebhookUrlChanged,
            onNotifyHourChanged = vm::onNotificationHourChanged,
            onPrefixChanged = vm::onNotificationPrefixChanged,
            onSave = vm::onSaveNotificationSettings,
            onRetry = vm::loadNotificationSettings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GarbageScheduleCard(
    isLoading: Boolean,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    schedules: List<GarbageTypeSchedule>,
    garbageMessage: String?,
    garbageSaving: Boolean,
    onToggleDay: (GarbageType, Int) -> Unit,
    onFrequencyChange: (GarbageType, CollectionFrequency) -> Unit,
    onSaveClick: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        LoadableCardContent(isLoading = isLoading, loadError = loadError, loadErrorMessage = loadErrorMessage, onRetry = onRetry) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (schedule in schedules) {
                    val garbageType = schedule.garbageType
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = garbageType.icon, contentDescription = null, tint = garbageType.color)
                            Text(
                                text = garbageType.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = "収集曜日",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (dayIndex in 0..6) {
                                val selected = dayIndex in schedule.daysOfWeek
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleDay(garbageType, dayIndex) },
                                    label = { Text(dayLabels[dayIndex]) },
                                    border =
                                        if (selected) {
                                            BorderStroke(1.dp, garbageType.color)
                                        } else {
                                            FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
                                        },
                                )
                            }
                        }

                        Text(
                            text = "収集頻度",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow {
                            CollectionFrequency.entries.forEachIndexed { index, freq ->
                                SegmentedButton(
                                    selected = schedule.frequency == freq,
                                    onClick = { onFrequencyChange(garbageType, freq) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = CollectionFrequency.entries.size),
                                ) {
                                    Text(text = freq.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (schedule != schedules.lastOrNull()) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (garbageMessage != null) {
                    Text(text = garbageMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Button(onClick = onSaveClick, modifier = Modifier.height(48.dp), enabled = !garbageSaving) {
                    if (garbageSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("保存する")
                    }
                }
            }
        }
    }
}

@Composable
private fun GarbageNotificationCard(
    isLoading: Boolean,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    enabled: Boolean,
    webhookUrl: String,
    notifyHour: String,
    prefix: String,
    isSaving: Boolean,
    isHourValid: Boolean,
    message: String?,
    onEnabledChanged: (Boolean) -> Unit,
    onWebhookUrlChanged: (String) -> Unit,
    onNotifyHourChanged: (String) -> Unit,
    onPrefixChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    WebhookSettingsCard(
        isLoading = isLoading,
        title = "リマインダー通知",
        featureEnabled = enabled,
        url = webhookUrl,
        onUrlChanged = onWebhookUrlChanged,
        isSaving = isSaving,
        onEnabledChanged = onEnabledChanged,
        onSave = onSave,
        modifier = modifier,
        loadError = loadError,
        loadErrorMessage = loadErrorMessage,
        onRetry = onRetry,
        description = "この時刻に翌日のゴミ出し情報を通知します。ダッシュボードの表示切替は毎日 10:00 固定です。",
        message = prefix,
        onMessageChanged = onPrefixChanged,
        messagePlaceholder = "",
        statusMessage = message,
        saveEnabled = !isSaving && isHourValid,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "通知時刻", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
            OutlinedTextField(
                value = notifyHour,
                onValueChange = { v -> onNotifyHourChanged(v.filter { it.isDigit() }.take(2)) },
                label = { Text("時") },
                singleLine = true,
                isError = !isHourValid,
                modifier = Modifier.width(72.dp),
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            )
            Text(": 00", style = MaterialTheme.typography.titleMedium)
        }
    }
}
