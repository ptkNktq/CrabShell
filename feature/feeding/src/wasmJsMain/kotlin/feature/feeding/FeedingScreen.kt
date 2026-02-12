@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.feeding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.components.CalendarView
import core.ui.theme.FeedingDoneColor
import core.ui.theme.color
import core.ui.theme.icon
import core.ui.theme.label
import core.ui.util.dayOfWeekShortJs
import core.ui.util.todayDateJs
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
            text = "ごはん記録",
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
                        Text("エラー: ${vm.error}", color = MaterialTheme.colorScheme.error)
                    }

                    else -> {
                        // 朝・昼・晩のカード
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (mealTime in MealTime.entries) {
                                MealCard(
                                    mealTime = mealTime,
                                    feeding = vm.log.feedings[mealTime] ?: Feeding(),
                                    onFeed = { vm.feed(mealTime) },
                                )
                            }
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
    onFeed: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (feeding.done)
                FeedingDoneColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = mealTime.icon,
                    contentDescription = mealTime.label,
                    tint = mealTime.color,
                )
                Column {
                    Text(
                        text = mealTime.label,
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
                    contentDescription = "済み",
                    tint = FeedingDoneColor,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Button(onClick = onFeed) {
                    Text("あげる")
                }
            }
        }
    }
}

@Composable
private fun NoteSection(note: String, onNoteChange: (String) -> Unit, onSave: () -> Unit) {
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
        Text("保存")
    }
}
