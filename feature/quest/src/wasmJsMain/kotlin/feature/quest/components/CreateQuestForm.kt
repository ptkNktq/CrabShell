@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.quest.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.ui.components.CalendarView
import core.ui.extensions.icon
import core.ui.extensions.label
import core.ui.util.todayDateJs
import model.QuestCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateQuestForm(
    onSubmit: (
        title: String,
        description: String,
        category: QuestCategory,
        rewardPoints: Int,
        deadline: String?,
    ) -> Unit,
    onCancel: () -> Unit,
    isAiAvailable: Boolean = false,
    isGenerating: Boolean = false,
    onGenerateText: (String, QuestCategory, Int, String?, (String) -> Unit) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    enabled: Boolean = true,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(QuestCategory.Other) }
    var rewardPointsText by remember { mutableStateOf("") }

    // 期限: 日付
    val today = remember { todayDateJs().toString() }
    var deadlineDate by remember { mutableStateOf("") }
    var showCalendar by remember { mutableStateOf(false) }

    // 期限: 時刻（オプション）
    var hasTime by remember { mutableStateOf(false) }
    var deadlineHour by remember { mutableStateOf("") }
    var deadlineMinute by remember { mutableStateOf("") }

    val isValid = title.isNotBlank() && (rewardPointsText.toIntOrNull() ?: 0) > 0

    // 期限文字列の組み立て
    val deadlineStr =
        if (deadlineDate.isBlank()) {
            null
        } else if (hasTime && deadlineHour.isNotBlank()) {
            val h = deadlineHour.padStart(2, '0')
            val m = (deadlineMinute.ifBlank { "00" }).padStart(2, '0')
            "$deadlineDate $h:$m"
        } else {
            deadlineDate
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "新しいクエスト",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showCloseButton) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タイトル") },
                placeholder = { Text("例: 洗濯物を干す") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // カテゴリ選択
            Text(
                "カテゴリ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuestCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) },
                        leadingIcon = {
                            Icon(
                                cat.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = rewardPointsText,
                onValueChange = { rewardPointsText = it.filter { c -> c.isDigit() } },
                label = { Text("報酬ポイント") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // 期限セクション
            Text(
                "期限（任意）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showCalendar = !showCalendar },
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(deadlineDate.ifBlank { "日付を選択" })
                }

                if (deadlineDate.isNotBlank()) {
                    TextButton(onClick = {
                        deadlineDate = ""
                        showCalendar = false
                        hasTime = false
                        deadlineHour = ""
                        deadlineMinute = ""
                    }) {
                        Text("クリア")
                    }
                }
            }

            AnimatedVisibility(visible = showCalendar) {
                CalendarView(
                    selectedDate = deadlineDate.ifBlank { today },
                    today = today,
                    onDateSelected = { selected ->
                        deadlineDate = selected
                        showCalendar = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }

            // 時刻入力（オプション）
            if (deadlineDate.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Checkbox(
                        checked = hasTime,
                        onCheckedChange = { hasTime = it },
                    )
                    Text(
                        "時刻を指定",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AnimatedVisibility(visible = hasTime) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        OutlinedTextField(
                            value = deadlineHour,
                            onValueChange = { v ->
                                val filtered = v.filter { it.isDigit() }.take(2)
                                if ((filtered.toIntOrNull() ?: 0) <= 23) {
                                    deadlineHour = filtered
                                }
                            },
                            label = { Text("時") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                        )
                        Text(
                            ":",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = deadlineMinute,
                            onValueChange = { v ->
                                val filtered = v.filter { it.isDigit() }.take(2)
                                if ((filtered.toIntOrNull() ?: 0) <= 59) {
                                    deadlineMinute = filtered
                                }
                            },
                            label = { Text("分") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // AI 生成ボタン
            if (isAiAvailable) {
                val canGenerate = title.isNotBlank() && !isGenerating
                Button(
                    onClick = {
                        onGenerateText(
                            title,
                            category,
                            rewardPointsText.toIntOrNull() ?: 0,
                            deadlineStr,
                        ) { generatedText ->
                            description = generatedText
                        }
                    },
                    enabled = canGenerate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("生成中...")
                    } else {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("AI で説明文を生成")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("説明文") },
                placeholder = { Text("RPG風のフレーバーテキスト（任意）") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            if (!enabled) {
                Text(
                    "同時に発行できるクエストは10件までです",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
            }

            Button(
                onClick = {
                    onSubmit(
                        title,
                        description,
                        category,
                        rewardPointsText.toIntOrNull() ?: 0,
                        deadlineStr,
                    )
                },
                enabled = isValid && enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("クエストを投稿")
            }
        }
    }
}
