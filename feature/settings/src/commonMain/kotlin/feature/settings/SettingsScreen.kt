package feature.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
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
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.extensions.color
import core.ui.extensions.icon
import core.ui.extensions.label
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule
import model.User
import model.WebhookEvent
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val dayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

@Composable
fun SettingsScreen(
    passwordVm: PasswordChangeViewModel = koinViewModel(),
    passkeyVm: PasskeyManagementViewModel = koinViewModel(),
) {
    val authStateHolder = koinInject<AuthStateHolder>()
    val isAdmin = authStateHolder.isAdmin
    val koin = getKoin()
    val userNameVm = remember(isAdmin) { if (isAdmin) koin.get<UserNameViewModel>() else null }
    val garbageVm = remember(isAdmin) { if (isAdmin) koin.get<GarbageScheduleViewModel>() else null }
    val webhookVm = remember(isAdmin) { if (isAdmin) koin.get<WebhookViewModel>() else null }
    val cacheVm = remember(isAdmin) { if (isAdmin) koin.get<CacheRefreshViewModel>() else null }
    val scrollState = rememberScrollState()
    val windowSizeClass = LocalWindowSizeClass.current

    SettingsContent(
        scrollState = scrollState,
        isAdmin = isAdmin,
        currentPassword = passwordVm.uiState.currentPassword,
        newPassword = passwordVm.uiState.newPassword,
        confirmPassword = passwordVm.uiState.confirmPassword,
        isLoading = passwordVm.uiState.isLoading,
        errorMessage = passwordVm.uiState.errorMessage,
        successMessage = passwordVm.uiState.successMessage,
        onCurrentPasswordChanged = passwordVm::onCurrentPasswordChanged,
        onNewPasswordChanged = passwordVm::onNewPasswordChanged,
        onConfirmPasswordChanged = passwordVm::onConfirmPasswordChanged,
        onChangePassword = passwordVm::onChangePassword,
        passkeyAvailable = passkeyVm.uiState.isAvailable,
        passkeyRegistering = passkeyVm.uiState.isRegistering,
        credentialCount = passkeyVm.uiState.credentialCount,
        passkeyError = passkeyVm.uiState.errorMessage,
        passkeySuccess = passkeyVm.uiState.successMessage,
        onRegisterPasskey = passkeyVm::onRegisterPasskey,
        users = userNameVm?.uiState?.users ?: emptyList(),
        usersSaving = userNameVm?.uiState?.isSaving ?: false,
        usersMessage = userNameVm?.uiState?.message,
        onUpdateDisplayName = { uid, name -> userNameVm?.onUpdateDisplayName(uid, name) },
        garbageLoading = garbageVm?.uiState?.isLoading ?: false,
        garbageSchedules = garbageVm?.uiState?.schedules ?: emptyList(),
        garbageMessage = garbageVm?.uiState?.message,
        garbageSaving = garbageVm?.uiState?.isSaving ?: false,
        onToggleDay = { type, day -> garbageVm?.onToggleDay(type, day) },
        onFrequencyChange = { type, freq -> garbageVm?.onChangeFrequency(type, freq) },
        onSaveGarbageSchedule = { garbageVm?.onSaveSchedule() },
        webhookLoading = webhookVm?.uiState?.isLoading ?: false,
        webhookUrl = webhookVm?.uiState?.url ?: "",
        webhookEnabled = webhookVm?.uiState?.enabled ?: false,
        webhookEvents = webhookVm?.uiState?.events ?: emptyList(),
        webhookSaving = webhookVm?.uiState?.isSaving ?: false,
        webhookMessage = webhookVm?.uiState?.message,
        onWebhookUrlChanged = { webhookVm?.onUrlChanged(it) },
        onWebhookEnabledChanged = { webhookVm?.onEnabledChanged(it) },
        onWebhookToggleEvent = { webhookVm?.onToggleEvent(it) },
        onSaveWebhook = { webhookVm?.onSave() },
        cacheClearing = cacheVm?.uiState?.isClearing ?: false,
        cacheMessage = cacheVm?.uiState?.message,
        onClearCache = { cacheVm?.onClearCache() },
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
    passkeyAvailable: Boolean = false,
    passkeyRegistering: Boolean = false,
    credentialCount: Int = 0,
    passkeyError: String? = null,
    passkeySuccess: String? = null,
    onRegisterPasskey: () -> Unit = {},
    users: List<User>,
    usersSaving: Boolean,
    usersMessage: String?,
    onUpdateDisplayName: (String, String) -> Unit,
    garbageLoading: Boolean,
    garbageSchedules: List<GarbageTypeSchedule>,
    garbageMessage: String?,
    garbageSaving: Boolean,
    onToggleDay: (GarbageType, Int) -> Unit,
    onFrequencyChange: (GarbageType, CollectionFrequency) -> Unit,
    onSaveGarbageSchedule: () -> Unit,
    webhookLoading: Boolean = false,
    webhookUrl: String = "",
    webhookEnabled: Boolean = false,
    webhookEvents: List<String> = emptyList(),
    webhookSaving: Boolean = false,
    webhookMessage: String? = null,
    onWebhookUrlChanged: (String) -> Unit = {},
    onWebhookEnabledChanged: (Boolean) -> Unit = {},
    onWebhookToggleEvent: (String) -> Unit = {},
    onSaveWebhook: () -> Unit = {},
    cacheClearing: Boolean = false,
    cacheMessage: String? = null,
    onClearCache: () -> Unit = {},
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

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
                if (passkeyAvailable) {
                    PasskeyManagementCard(
                        credentialCount = credentialCount,
                        isRegistering = passkeyRegistering,
                        errorMessage = passkeyError,
                        successMessage = passkeySuccess,
                        onRegisterPasskey = onRegisterPasskey,
                        modifier = cardModifier,
                    )
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(32.dp))

                // ユーザー名管理セクション（管理者のみ）
                SettingsSection(title = "ユーザー名管理", badge = "管理者") {
                    UserNameManagementCard(
                        users = users,
                        usersSaving = usersSaving,
                        usersMessage = usersMessage,
                        onUpdateDisplayName = onUpdateDisplayName,
                        modifier = cardModifier,
                    )
                }

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

                Spacer(modifier = Modifier.height(32.dp))

                // Webhook 設定セクション（管理者のみ）
                SettingsSection(title = "Webhook 通知", badge = "管理者") {
                    if (webhookLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        WebhookSettingsCard(
                            url = webhookUrl,
                            enabled = webhookEnabled,
                            events = webhookEvents,
                            isSaving = webhookSaving,
                            message = webhookMessage,
                            onUrlChanged = onWebhookUrlChanged,
                            onEnabledChanged = onWebhookEnabledChanged,
                            onToggleEvent = onWebhookToggleEvent,
                            onSave = onSaveWebhook,
                            modifier = cardModifier,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // サーバーキャッシュセクション（管理者のみ）
                SettingsSection(title = "サーバーキャッシュ", badge = "管理者") {
                    CacheRefreshCard(
                        isClearing = cacheClearing,
                        message = cacheMessage,
                        onClearCache = onClearCache,
                        modifier = cardModifier,
                    )
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
private fun UserNameManagementCard(
    users: List<User>,
    usersSaving: Boolean,
    usersMessage: String?,
    onUpdateDisplayName: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ローカル編集状態: uid -> 入力中の displayName
    var editedNames by remember(users) {
        mutableStateOf(users.associate { it.uid to (it.displayName ?: "") })
    }

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (users.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                for (user in users) {
                    OutlinedTextField(
                        value = editedNames[user.uid] ?: "",
                        onValueChange = { value ->
                            editedNames =
                                editedNames.toMutableMap().apply {
                                    put(user.uid, value)
                                }
                        },
                        label = { Text(user.uid) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !usersSaving,
                    )
                }

                if (usersMessage != null) {
                    Text(
                        text = usersMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = {
                        for (user in users) {
                            val newName = editedNames[user.uid] ?: ""
                            val oldName = user.displayName ?: ""
                            if (newName != oldName) {
                                onUpdateDisplayName(user.uid, newName)
                            }
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    enabled = !usersSaving,
                ) {
                    if (usersSaving) {
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
        colors =
            CardDefaults.cardColors(
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
                                border =
                                    if (selected) {
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
                                shape =
                                    SegmentedButtonDefaults.itemShape(
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
        colors =
            CardDefaults.cardColors(
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
                keyboardOptions =
                    KeyboardOptions(
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
                keyboardOptions =
                    KeyboardOptions(
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
                keyboardOptions =
                    KeyboardOptions(
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

@Composable
private fun PasskeyManagementCard(
    credentialCount: Int,
    isRegistering: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onRegisterPasskey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
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
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "パスキー管理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = "登録済み: $credentialCount 件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "別の端末やブラウザからログインするには、パスキーを追加してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                onClick = onRegisterPasskey,
                modifier = Modifier.height(48.dp),
                enabled = !isRegistering,
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("パスキーを追加")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebhookSettingsCard(
    url: String,
    enabled: Boolean,
    events: List<String>,
    isSaving: Boolean,
    message: String?,
    onUrlChanged: (String) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onToggleEvent: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
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
                Text("Webhook 設定", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChanged,
                label = { Text("Webhook URL") },
                placeholder = { Text("https://discord.com/api/webhooks/...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            )

            Text("通知するイベント", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WebhookEvent.all.forEach { event ->
                    FilterChip(
                        selected = event in events,
                        onClick = { onToggleEvent(event) },
                        label = { Text(WebhookEvent.label(event)) },
                    )
                }
            }

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

@Composable
private fun CacheRefreshCard(
    isClearing: Boolean,
    message: String?,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "データの不整合が疑われる場合に、サーバー側のキャッシュを手動でクリアします。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = onClearCache,
                modifier = Modifier.height(48.dp),
                enabled = !isClearing,
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("キャッシュをクリア")
                }
            }
        }
    }
}
