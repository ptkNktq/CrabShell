package feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import core.ui.theme.color
import core.ui.theme.icon
import core.ui.theme.label
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule

private val dayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { SettingsViewModel(scope) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "設定",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // アカウントセクション
        SettingsSection(title = "アカウント") {
            PasswordChangeCard(vm)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ゴミ出しセクション
        SettingsSection(title = "ゴミ出し") {
            if (vm.garbageLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (schedule in vm.garbageSchedules) {
                        GarbageScheduleCard(
                            schedule = schedule,
                            onToggleDay = { day -> vm.toggleDay(schedule.garbageType, day) },
                            onFrequencyChange = { freq -> vm.changeFrequency(schedule.garbageType, freq) },
                        )
                    }

                    if (vm.garbageMessage != null) {
                        Text(
                            text = vm.garbageMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Button(
                        onClick = vm::saveGarbageSchedule,
                        modifier = Modifier.height(48.dp),
                        enabled = !vm.garbageSaving,
                    ) {
                        if (vm.garbageSaving) {
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
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun GarbageScheduleCard(
    schedule: GarbageTypeSchedule,
    onToggleDay: (Int) -> Unit,
    onFrequencyChange: (CollectionFrequency) -> Unit,
) {
    val garbageType = schedule.garbageType

    Card(
        modifier = Modifier.widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = garbageType.icon,
                    contentDescription = null,
                    tint = garbageType.color,
                )
                Text(
                    text = garbageType.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 曜日チップ
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
                        onClick = { onToggleDay(dayIndex) },
                        label = { Text(dayLabels[dayIndex]) },
                        border = if (selected) {
                            BorderStroke(1.dp, garbageType.color)
                        } else {
                            FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
                        },
                    )
                }
            }

            // 頻度セレクタ
            Text(
                text = "収集頻度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow {
                CollectionFrequency.entries.forEachIndexed { index, freq ->
                    SegmentedButton(
                        selected = schedule.frequency == freq,
                        onClick = { onFrequencyChange(freq) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = CollectionFrequency.entries.size,
                        ),
                    ) {
                        Text(
                            text = (freq as CollectionFrequency).label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordChangeCard(vm: SettingsViewModel) {
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "パスワード変更",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = vm.currentPassword,
                onValueChange = vm::onCurrentPasswordChanged,
                label = { Text("現在のパスワード") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                        Icon(
                            if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (currentPasswordVisible) "パスワードを隠す" else "パスワードを表示",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.isLoading,
            )

            OutlinedTextField(
                value = vm.newPassword,
                onValueChange = vm::onNewPasswordChanged,
                label = { Text("新しいパスワード") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (newPasswordVisible) "パスワードを隠す" else "パスワードを表示",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.isLoading,
            )

            OutlinedTextField(
                value = vm.confirmPassword,
                onValueChange = vm::onConfirmPasswordChanged,
                label = { Text("新しいパスワード（確認）") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "パスワードを隠す" else "パスワードを表示",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.isLoading,
            )

            if (vm.errorMessage != null) {
                Text(
                    text = vm.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (vm.successMessage != null) {
                Text(
                    text = vm.successMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = vm::changePassword,
                modifier = Modifier.height(48.dp),
                enabled = !vm.isLoading,
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("変更する")
                }
            }
        }
    }
}
