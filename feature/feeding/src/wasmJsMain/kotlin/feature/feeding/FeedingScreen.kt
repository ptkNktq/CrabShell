@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.feeding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import model.Feeding
import model.MealTime

@JsFun("(iso) => { const d = new Date(iso); return d.toLocaleTimeString('ja-JP', {hour:'2-digit', minute:'2-digit', hour12:false, timeZone:'Asia/Tokyo'}); }")
private external fun toJstHHMM(iso: JsString): JsString

@Composable
fun FeedingScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { FeedingViewModel(scope) }

    val today = remember { todayDateJs().toString() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(
            text = "Feeding Log",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            // 左側: カレンダー
            CalendarView(
                selectedDate = vm.selectedDate,
                today = today,
                onDateSelected = { vm.loadLog(it) },
                modifier = Modifier.width(300.dp),
            )

            Spacer(modifier = Modifier.width(24.dp))

            // 右側: 既存コンテンツ
            Column(modifier = Modifier.weight(1f)) {
                // 日付セレクター
                DateSelector(
                    date = vm.selectedDate,
                    onPrevious = { vm.goToPreviousDay() },
                    onNext = { vm.goToNextDay() },
                )

                Spacer(modifier = Modifier.height(24.dp))

                when {
                    vm.loading -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    vm.error != null -> {
                        Text("Error: ${vm.error}", color = MaterialTheme.colorScheme.error)
                    }

                    else -> {
                        // 朝・昼・晩のカード
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MealCard(
                                mealTime = MealTime.MORNING,
                                label = "Morning",
                                icon = Icons.Default.WbTwilight,
                                iconTint = Color(0xCDFF4E4E),
                                feeding = vm.log.feedings[MealTime.MORNING] ?: Feeding(),
                                onFeed = { vm.feed(MealTime.MORNING) },
                            )
                            MealCard(
                                mealTime = MealTime.LUNCH,
                                label = "Lunch",
                                icon = Icons.Default.WbSunny,
                                iconTint = Color(0xFFFBC02D),
                                feeding = vm.log.feedings[MealTime.LUNCH] ?: Feeding(),
                                onFeed = { vm.feed(MealTime.LUNCH) },
                            )
                            MealCard(
                                mealTime = MealTime.EVENING,
                                label = "Evening",
                                icon = Icons.Default.Bedtime,
                                iconTint = Color(0xFF5C6BC0),
                                feeding = vm.log.feedings[MealTime.EVENING] ?: Feeding(),
                                onFeed = { vm.feed(MealTime.EVENING) },
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 備考欄
                        NoteSection(
                            note = vm.noteDraft,
                            onNoteChange = { vm.updateNoteDraft(it) },
                            onSave = { vm.saveNote() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelector(date: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Text(
            text = date,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
private fun MealCard(
    mealTime: MealTime,
    label: String,
    icon: ImageVector,
    iconTint: Color,
    feeding: Feeding,
    onFeed: () -> Unit,
) {
    val doneColor = Color(0xFF4CAF50)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (feeding.done)
                doneColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (feeding.done) doneColor else iconTint,
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val ts = feeding.timestamp
                    if (feeding.done && ts != null) {
                        Text(
                            text = toJstHHMM(ts.toJsString()).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (feeding.done) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = doneColor,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Button(onClick = onFeed) {
                    Text("Feed")
                }
            }
        }
    }
}

@Composable
private fun NoteSection(note: String, onNoteChange: (String) -> Unit, onSave: () -> Unit) {
    Text(
        text = "Note",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("How was the meal today?") },
        minLines = 2,
        maxLines = 4,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onSave) {
        Text("Save Note")
    }
}
