package feature.petmanagement

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.extensions.icon
import core.ui.extensions.label
import model.MealTime
import model.Pet
import org.koin.compose.viewmodel.koinViewModel

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
        onMoveMealUp = viewModel::onMoveMealUp,
        onMoveMealDown = viewModel::onMoveMealDown,
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
    onMoveMealUp: (Int) -> Unit,
    onMoveMealDown: (Int) -> Unit,
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

            // ごはんの順序セクション
            SectionTitle("ごはんの順序")
            MealOrderCard(
                mealOrder = mealOrder,
                onMoveMealUp = onMoveMealUp,
                onMoveMealDown = onMoveMealDown,
                modifier = cardModifier,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ごはんの時刻セクション
            SectionTitle("ごはんの時刻")
            MealTimesCard(
                mealTimes = mealTimes,
                isSaving = isSaving,
                onMealTimeChanged = onMealTimeChanged,
                modifier = cardModifier,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // リマインダー通知セクション
            SectionTitle("リマインダー通知")
            ReminderCard(
                reminderEnabled = reminderEnabled,
                reminderDelayMinutes = reminderDelayMinutes,
                reminderPrefix = reminderPrefix,
                isSaving = isSaving,
                onReminderEnabledChanged = onReminderEnabledChanged,
                onReminderDelayChanged = onReminderDelayChanged,
                onReminderPrefixChanged = onReminderPrefixChanged,
                modifier = cardModifier,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // メッセージ
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = cardModifier,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ごはん設定保存ボタン
            Button(
                onClick = onSaveFeedingSettings,
                modifier = cardModifier.height(48.dp),
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("ごはん設定を保存")
                }
            }
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
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
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
private fun MealOrderCard(
    mealOrder: List<MealTime>,
    onMoveMealUp: (Int) -> Unit,
    onMoveMealDown: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "ごはん画面での表示順を設定します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            mealOrder.forEachIndexed { index, mealTime ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = mealTime.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = mealTime.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onMoveMealUp(index) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "上へ")
                    }
                    IconButton(
                        onClick = { onMoveMealDown(index) },
                        enabled = index < mealOrder.size - 1,
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "下へ")
                    }
                }
            }
        }
    }
}

@Composable
private fun MealTimesCard(
    mealTimes: Map<MealTime, String>,
    isSaving: Boolean,
    onMealTimeChanged: (MealTime, String) -> Unit,
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
            for (mealTime in MealTime.entries) {
                OutlinedTextField(
                    value = mealTimes[mealTime] ?: "",
                    onValueChange = { onMealTimeChanged(mealTime, it) },
                    label = { Text("${mealTime.label}ごはんの時刻") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    leadingIcon = {
                        Icon(
                            imageVector = mealTime.icon,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminderEnabled: Boolean,
    reminderDelayMinutes: Int,
    reminderPrefix: String,
    isSaving: Boolean,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDelayChanged: (String) -> Unit,
    onReminderPrefixChanged: (String) -> Unit,
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
            )

            OutlinedTextField(
                value = reminderPrefix,
                onValueChange = onReminderPrefixChanged,
                label = { Text("通知プレフィックス") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            )
        }
    }
}
