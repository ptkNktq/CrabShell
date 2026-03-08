package feature.feeding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.components.CalendarView
import core.ui.extensions.FeedingDoneColor
import core.ui.extensions.color
import core.ui.extensions.icon
import core.ui.extensions.label
import core.ui.util.dayOfWeekShortJs
import core.ui.util.toJstHHMM
import core.ui.util.toJstHour
import core.ui.util.toJstMinute
import core.ui.util.todayDateJs
import model.Feeding
import model.FeedingLog
import model.MealTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedingScreen(vm: FeedingViewModel = koinViewModel()) {
    val today = remember { todayDateJs().toString() }
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
    )
}

@Composable
internal fun FeedingContent(
    petName: String?,
    selectedDate: String,
    today: String,
    loading: Boolean,
    error: String?,
    log: FeedingLog,
    mealOrder: List<MealTime>,
    noteDraft: String,
    editingMealTime: MealTime?,
    onDateSelected: (String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onFeed: (MealTime) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onStartEditTimestamp: (MealTime) -> Unit,
    onCancelEditTimestamp: () -> Unit,
    onSaveTimestamp: (MealTime, Int, Int) -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    Column(
        modifier = Modifier.fillMaxSize().padding(if (isCompact) 12.dp else 24.dp),
    ) {
        Text(
            text = petName?.let { "$it のごはん記録" } ?: "ごはん記録",
            style =
                if (isCompact) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        if (isCompact) {
            // Compact: カレンダーなし、詳細のみ（縦スクロール）
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                FeedingDetailSection(
                    selectedDate = selectedDate,
                    loading = loading,
                    error = error,
                    log = log,
                    mealOrder = mealOrder,
                    noteDraft = noteDraft,
                    editingMealTime = editingMealTime,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onFeed = onFeed,
                    onNoteChange = onNoteChange,
                    onSaveNote = onSaveNote,
                    onStartEditTimestamp = onStartEditTimestamp,
                    onCancelEditTimestamp = onCancelEditTimestamp,
                    onSaveTimestamp = onSaveTimestamp,
                )
            }
        } else {
            // Medium/Expanded: 詳細左（メイン）、カレンダー右（操作補助）
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FeedingDetailSection(
                        selectedDate = selectedDate,
                        loading = loading,
                        error = error,
                        log = log,
                        noteDraft = noteDraft,
                        editingMealTime = editingMealTime,
                        onPreviousDay = onPreviousDay,
                        onNextDay = onNextDay,
                        onFeed = onFeed,
                        onNoteChange = onNoteChange,
                        onSaveNote = onSaveNote,
                        onStartEditTimestamp = onStartEditTimestamp,
                        onCancelEditTimestamp = onCancelEditTimestamp,
                        onSaveTimestamp = onSaveTimestamp,
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                CalendarView(
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelected = onDateSelected,
                    modifier = Modifier.width(300.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedingDetailSection(
    selectedDate: String,
    loading: Boolean,
    error: String?,
    log: FeedingLog,
    mealOrder: List<MealTime>,
    noteDraft: String,
    editingMealTime: MealTime?,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onFeed: (MealTime) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onStartEditTimestamp: (MealTime) -> Unit,
    onCancelEditTimestamp: () -> Unit,
    onSaveTimestamp: (MealTime, Int, Int) -> Unit,
) {
    DateSelector(
        date = selectedDate,
        onPrevious = onPreviousDay,
        onNext = onNextDay,
    )

    Spacer(modifier = Modifier.height(24.dp))

    when {
        loading -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Text("エラー: $error", color = MaterialTheme.colorScheme.error)
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (mealTime in mealOrder) {
                    MealCard(
                        mealTime = mealTime,
                        feeding = log.feedings[mealTime] ?: Feeding(),
                        isEditing = editingMealTime == mealTime,
                        onFeed = { onFeed(mealTime) },
                        onStartEdit = { onStartEditTimestamp(mealTime) },
                        onCancelEdit = onCancelEditTimestamp,
                        onSaveTimestamp = { hour, minute -> onSaveTimestamp(mealTime, hour, minute) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            NoteSection(
                note = noteDraft,
                onNoteChange = onNoteChange,
                onSave = onSaveNote,
            )
        }
    }
}

@Composable
private fun DateSelector(
    date: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val dow = dayOfWeekShortJs(date.toJsString()).toString()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前日")
        }
        Text(
            text = "$date ($dow)",
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "翌日")
        }
    }
}

@Composable
private fun MealCard(
    mealTime: MealTime,
    feeding: Feeding,
    isEditing: Boolean,
    onFeed: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveTimestamp: (hour: Int, minute: Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (feeding.done) {
                        FeedingDoneColor.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左側: アイコン + ラベル
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = mealTime.icon,
                    contentDescription = mealTime.label,
                    tint = mealTime.color,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = mealTime.label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // 右側: 時刻 + アクション
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ts = feeding.timestamp
                if (feeding.done && ts != null) {
                    if (isEditing) {
                        TimestampEditor(
                            timestamp = ts,
                            onCancel = onCancelEdit,
                            onSave = onSaveTimestamp,
                        )
                    } else {
                        TimestampBadge(
                            timestamp = ts,
                            onClick = onStartEdit,
                        )
                    }
                }

                if (feeding.done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "済み",
                        tint = FeedingDoneColor,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Button(onClick = onFeed) {
                        Text("あげる")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimestampBadge(
    timestamp: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    ) {
        Text(
            text = toJstHHMM(timestamp.toJsString()).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TimestampEditor(
    timestamp: String,
    onCancel: () -> Unit,
    onSave: (hour: Int, minute: Int) -> Unit,
) {
    val initialHour =
        remember(timestamp) {
            toJstHour(timestamp.toJsString()).toString().trim().toIntOrNull() ?: 0
        }
    val initialMinute =
        remember(timestamp) {
            toJstMinute(timestamp.toJsString()).toString().trim().toIntOrNull() ?: 0
        }
    var hourText by remember(timestamp) { mutableStateOf(initialHour.toString().padStart(2, '0')) }
    var minuteText by remember(timestamp) { mutableStateOf(initialMinute.toString().padStart(2, '0')) }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
        ) {
            OutlinedTextField(
                value = hourText,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hourText = it },
                modifier = Modifier.width(48.dp),
                textStyle = MaterialTheme.typography.labelMedium,
                singleLine = true,
            )
            Text(
                ":",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minuteText = it },
                modifier = Modifier.width(48.dp),
                textStyle = MaterialTheme.typography.labelMedium,
                singleLine = true,
            )
            IconButton(
                onClick = {
                    val h = hourText.toIntOrNull()
                    val m = minuteText.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) {
                        onSave(h, m)
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "保存",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "キャンセル",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        text = "メモ",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("今日の様子はどうだった？") },
        minLines = 2,
        maxLines = 4,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onSave) {
        Text("保存する")
    }
}
