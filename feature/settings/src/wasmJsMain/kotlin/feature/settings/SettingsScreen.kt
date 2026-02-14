package feature.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
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
    val scrollState = rememberScrollState()
    val isAdmin = (AuthStateHolder.state as? AuthState.Authenticated)?.user?.isAdmin == true
    val windowSizeClass = LocalWindowSizeClass.current

    SettingsContent(
        scrollState = scrollState,
        isAdmin = isAdmin,
        currentPassword = vm.currentPassword,
        newPassword = vm.newPassword,
        confirmPassword = vm.confirmPassword,
        isLoading = vm.isLoading,
        errorMessage = vm.errorMessage,
        successMessage = vm.successMessage,
        onCurrentPasswordChanged = vm::onCurrentPasswordChanged,
        onNewPasswordChanged = vm::onNewPasswordChanged,
        onConfirmPasswordChanged = vm::onConfirmPasswordChanged,
        onChangePassword = vm::changePassword,
        garbageLoading = vm.garbageLoading,
        garbageSchedules = vm.garbageSchedules,
        garbageMessage = vm.garbageMessage,
        garbageSaving = vm.garbageSaving,
        onToggleDay = vm::toggleDay,
        onFrequencyChange = vm::changeFrequency,
        onSaveGarbageSchedule = vm::saveGarbageSchedule,
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun SettingsContent(
    scrollState: ScrollState,
    isAdmin: Boolean,
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onChangePassword: () -> Unit,
    garbageLoading: Boolean,
    garbageSchedules: List<GarbageTypeSchedule>,
    garbageMessage: String?,
    garbageSaving: Boolean,
    onToggleDay: (GarbageType, Int) -> Unit,
    onFrequencyChange: (GarbageType, CollectionFrequency) -> Unit,
    onSaveGarbageSchedule: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 16.dp else 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = if (isCompact) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            val cardModifier = if (isCompact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 480.dp)

            // アカウントセクション
            SettingsSection(title = "アカウント") {
                PasswordChangeCard(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    confirmPassword = confirmPassword,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    onCurrentPasswordChanged = onCurrentPasswordChanged,
                    onNewPasswordChanged = onNewPasswordChanged,
                    onConfirmPasswordChanged = onConfirmPasswordChanged,
                    onChangePassword = onChangePassword,
                    modifier = cardModifier,
                )
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(32.dp))

                // ゴミ出しセクション（管理者のみ）
                SettingsSection(title = "ゴミ出し", badge = "管理者") {
                    if (garbageLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        GarbageScheduleCard(
                            schedules = garbageSchedules,
                            garbageMessage = garbageMessage,
                            garbageSaving = garbageSaving,
                            onToggleDay = onToggleDay,
                            onFrequencyChange = onFrequencyChange,
                            onSaveClick = onSaveGarbageSchedule,
                            modifier = cardModifier,
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
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
private fun SettingsSection(
    title: String,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (badge != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun GarbageScheduleCard(
    schedules: List<GarbageTypeSchedule>,
    garbageMessage: String?,
    garbageSaving: Boolean,
    onToggleDay: (GarbageType, Int) -> Unit,
    onFrequencyChange: (GarbageType, CollectionFrequency) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (schedule in schedules) {
                val garbageType = schedule.garbageType
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                                onClick = { onToggleDay(garbageType, dayIndex) },
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
                                onClick = { onFrequencyChange(garbageType, freq) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = CollectionFrequency.entries.size,
                                ),
                            ) {
                                Text(
                                    text = freq.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                if (schedule != schedules.lastOrNull()) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (garbageMessage != null) {
                Text(
                    text = garbageMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier.height(48.dp),
                enabled = !garbageSaving,
            ) {
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

@Composable
private fun PasswordChangeCard(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
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
                value = currentPassword,
                onValueChange = onCurrentPasswordChanged,
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
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChanged,
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
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChanged,
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
                enabled = !isLoading,
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (successMessage != null) {
                Text(
                    text = successMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onChangePassword,
                modifier = Modifier.height(48.dp),
                enabled = !isLoading,
            ) {
                if (isLoading) {
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
