package feature.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.ui.extensions.label
import model.MealTime
import model.Pet

private val mealOrderPresets =
    listOf(
        listOf(MealTime.MORNING, MealTime.LUNCH, MealTime.EVENING) to "朝→昼→晩",
        listOf(MealTime.LUNCH, MealTime.EVENING, MealTime.MORNING) to "昼→晩→朝",
        listOf(MealTime.EVENING, MealTime.MORNING, MealTime.LUNCH) to "晩→朝→昼",
    )

@Composable
internal fun PetNameCard(
    pets: List<Pet>,
    editingPetNames: Map<String, String>,
    isSaving: Boolean,
    onPetNameChanged: (String, String) -> Unit,
    onSavePetName: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ペット名", style = MaterialTheme.typography.titleSmall)
            for (pet in pets) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = editingPetNames[pet.id] ?: pet.name,
                        onValueChange = { onPetNameChanged(pet.id, it) },
                        label = { Text("ペット名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                    )
                    val nameChanged = (editingPetNames[pet.id] ?: pet.name) != pet.name
                    Button(
                        onClick = { onSavePetName(pet.id) },
                        enabled = !isSaving && nameChanged,
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedingSettingsCard(
    mealOrder: List<MealTime>,
    mealTimes: Map<MealTime, String>,
    reminderEnabled: Boolean,
    reminderWebhookUrl: String,
    reminderDelayMinutes: Int,
    reminderPrefix: String,
    isSaving: Boolean,
    isTesting: Boolean,
    message: String?,
    onMealOrderChanged: (List<MealTime>) -> Unit,
    onMealTimeChanged: (MealTime, String) -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderWebhookUrlChanged: (String) -> Unit,
    onReminderDelayMinutesChanged: (Int) -> Unit,
    onReminderPrefixChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTestReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ごはん設定", style = MaterialTheme.typography.titleSmall)

            // --- 表示順 ---
            Text("表示順", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "ごはん画面での表示順を設定します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                mealOrderPresets.forEachIndexed { index, (order, label) ->
                    SegmentedButton(
                        selected = mealOrder == order,
                        onClick = { onMealOrderChanged(order) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = mealOrderPresets.size,
                            ),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // --- 時刻 ---
            Text("予定時刻", style = MaterialTheme.typography.bodyMedium)
            for (mealTime in MealTime.entries) {
                val timeStr = mealTimes[mealTime] ?: "00:00"
                val parts = timeStr.split(":")
                val hour = parts.getOrElse(0) { "00" }
                val minute = parts.getOrElse(1) { "00" }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${mealTime.label}ごはん",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp),
                    )
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { h ->
                            val filtered = h.filter { it.isDigit() }.take(2)
                            onMealTimeChanged(mealTime, "$filtered:$minute")
                        },
                        label = { Text("時") },
                        singleLine = true,
                        modifier = Modifier.width(72.dp),
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    )
                    Text(":", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { m ->
                            val filtered = m.filter { it.isDigit() }.take(2)
                            onMealTimeChanged(mealTime, "$hour:$filtered")
                        },
                        label = { Text("分") },
                        singleLine = true,
                        modifier = Modifier.width(72.dp),
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // --- リマインダー通知 ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("リマインダー通知", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = onReminderEnabledChanged,
                    enabled = !isSaving,
                )
            }
            OutlinedTextField(
                value = reminderWebhookUrl,
                onValueChange = onReminderWebhookUrlChanged,
                label = { Text("Webhook URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && reminderEnabled,
            )
            OutlinedTextField(
                value = reminderPrefix,
                onValueChange = onReminderPrefixChanged,
                label = { Text("通知テキスト") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && reminderEnabled,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "遅延（分）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(80.dp),
                )
                OutlinedTextField(
                    value = reminderDelayMinutes.toString(),
                    onValueChange = { v ->
                        val filtered = v.filter { it.isDigit() }.take(3)
                        val minutes = filtered.toIntOrNull() ?: 30
                        onReminderDelayMinutesChanged(minutes)
                    },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    enabled = !isSaving && reminderEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                )
            }

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.height(48.dp),
                    enabled = !isSaving && !isTesting,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("保存する")
                    }
                }
                OutlinedButton(
                    onClick = onTestReminder,
                    modifier = Modifier.height(48.dp),
                    enabled = !isSaving && !isTesting && reminderEnabled && reminderWebhookUrl.isNotBlank(),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("テスト送信")
                    }
                }
            }
        }
    }
}
