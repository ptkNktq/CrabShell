package feature.petmanagement

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.extensions.label
import model.MealTime
import model.Pet
import org.koin.compose.viewmodel.koinViewModel

/** 順序プリセット: 現実的な巡回パターンのみ */
private val mealOrderPresets =
    listOf(
        listOf(MealTime.MORNING, MealTime.LUNCH, MealTime.EVENING) to "朝→昼→晩",
        listOf(MealTime.LUNCH, MealTime.EVENING, MealTime.MORNING) to "昼→晩→朝",
        listOf(MealTime.EVENING, MealTime.MORNING, MealTime.LUNCH) to "晩→朝→昼",
    )

@Composable
fun PetManagementScreen(viewModel: PetManagementViewModel = koinViewModel()) {
    val scrollState = rememberScrollState()
    val windowSizeClass = LocalWindowSizeClass.current

    PetManagementContent(
        scrollState = scrollState,
        pets = viewModel.uiState.pets,
        editingPetNames = viewModel.uiState.editingPetNames,
        mealOrder = viewModel.uiState.mealOrder,
        mealTimes = viewModel.uiState.mealTimes,
        reminderEnabled = viewModel.uiState.reminderEnabled,
        reminderDelayMinutes = viewModel.uiState.reminderDelayMinutes,
        reminderPrefix = viewModel.uiState.reminderPrefix,
        isLoading = viewModel.uiState.isLoading,
        isSaving = viewModel.uiState.isSaving,
        message = viewModel.uiState.message,
        onPetNameChanged = viewModel::onPetNameChanged,
        onSavePetName = viewModel::onSavePetName,
        onMealOrderChanged = viewModel::onMealOrderChanged,
        onMealTimeChanged = viewModel::onMealTimeChanged,
        onReminderEnabledChanged = viewModel::onReminderEnabledChanged,
        onReminderDelayChanged = viewModel::onReminderDelayChanged,
        onReminderPrefixChanged = viewModel::onReminderPrefixChanged,
        onSaveFeedingSettings = viewModel::onSaveFeedingSettings,
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun PetManagementContent(
    scrollState: ScrollState,
    pets: List<Pet>,
    editingPetNames: Map<String, String>,
    mealOrder: List<MealTime>,
    mealTimes: Map<MealTime, String>,
    reminderEnabled: Boolean,
    reminderDelayMinutes: Int,
    reminderPrefix: String,
    isLoading: Boolean,
    isSaving: Boolean,
    message: String?,
    onPetNameChanged: (String, String) -> Unit,
    onSavePetName: (String) -> Unit,
    onMealOrderChanged: (List<MealTime>) -> Unit,
    onMealTimeChanged: (MealTime, String) -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDelayChanged: (String) -> Unit,
    onReminderPrefixChanged: (String) -> Unit,
    onSaveFeedingSettings: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(if (isCompact) 16.dp else 24.dp)
                    .verticalScroll(scrollState),
            horizontalAlignment = if (isCompact) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            val cardModifier = if (isCompact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 480.dp)

            // ペット名セクション
            SectionTitle("ペット名")
            PetNameCard(
                pets = pets,
                editingPetNames = editingPetNames,
                isSaving = isSaving,
                onPetNameChanged = onPetNameChanged,
                onSavePetName = onSavePetName,
                modifier = cardModifier,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ごはん設定セクション
            SectionTitle("ごはん設定")
            FeedingSettingsCard(
                mealOrder = mealOrder,
                mealTimes = mealTimes,
                reminderEnabled = reminderEnabled,
                reminderDelayMinutes = reminderDelayMinutes,
                reminderPrefix = reminderPrefix,
                isSaving = isSaving,
                message = message,
                onMealOrderChanged = onMealOrderChanged,
                onMealTimeChanged = onMealTimeChanged,
                onReminderEnabledChanged = onReminderEnabledChanged,
                onReminderDelayChanged = onReminderDelayChanged,
                onReminderPrefixChanged = onReminderPrefixChanged,
                onSave = onSaveFeedingSettings,
                modifier = cardModifier,
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style =
                ScrollbarStyle(
                    minimalHeight = 48.dp,
                    thickness = 8.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun PetNameCard(
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
private fun FeedingSettingsCard(
    mealOrder: List<MealTime>,
    mealTimes: Map<MealTime, String>,
    reminderEnabled: Boolean,
    reminderDelayMinutes: Int,
    reminderPrefix: String,
    isSaving: Boolean,
    message: String?,
    onMealOrderChanged: (List<MealTime>) -> Unit,
    onMealTimeChanged: (MealTime, String) -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDelayChanged: (String) -> Unit,
    onReminderPrefixChanged: (String) -> Unit,
    onSave: () -> Unit,
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
            // --- 表示順 ---
            Text("表示順", style = MaterialTheme.typography.titleSmall)
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
            Text("予定時刻", style = MaterialTheme.typography.titleSmall)
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

            // --- リマインダー ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("リマインダー通知", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = onReminderEnabledChanged,
                )
            }

            Text(
                text = "予定時刻から指定分数後にごはんが未記録の場合、Webhook で通知します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = reminderDelayMinutes.toString(),
                onValueChange = onReminderDelayChanged,
                label = { Text("遅延（分）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            OutlinedTextField(
                value = reminderPrefix,
                onValueChange = onReminderPrefixChanged,
                label = { Text("通知テキスト（メンション等）") },
                placeholder = { Text("@everyone") },
                supportingText = { Text("embed の前に表示されるテキスト") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            )

            // --- メッセージ＋保存 ---
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.height(48.dp),
                enabled = !isSaving,
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
        }
    }
}
