package feature.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.components.AdminBadge
import core.ui.components.LoadableCardContent
import core.ui.extensions.color
import core.ui.extensions.icon
import core.ui.extensions.label
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule
import model.QuestWebhookEvent
import model.User
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val dayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

@Composable
private fun settingsScrollbarStyle() =
    ScrollbarStyle(
        minimalHeight = 48.dp,
        thickness = 8.dp,
        shape = MaterialTheme.shapes.small,
        hoverDurationMillis = 300,
        unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )

internal enum class SettingsCategory(
    val title: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
) {
    Account("アカウント", Icons.Default.Person),
    UserManagement("ユーザー管理", Icons.Default.Group, adminOnly = true),
    Garbage("ゴミ出し", Icons.Default.DeleteSweep, adminOnly = true),
    QuestWebhook("クエスト Webhook 通知", Icons.Default.Notifications, adminOnly = true),
    Cache("サーバーキャッシュ", Icons.Default.Cached, adminOnly = true),
}

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
    val questWebhookVm = remember(isAdmin) { if (isAdmin) koin.get<QuestWebhookViewModel>() else null }
    val cacheVm = remember(isAdmin) { if (isAdmin) koin.get<CacheRefreshViewModel>() else null }
    val windowSizeClass = LocalWindowSizeClass.current

    SettingsContent(
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
        usersLoading = userNameVm?.uiState?.isLoading ?: false,
        usersLoadError = userNameVm?.uiState?.loadError ?: false,
        users = userNameVm?.uiState?.users ?: emptyList(),
        usersSaving = userNameVm?.uiState?.isSaving ?: false,
        usersMessage = userNameVm?.uiState?.message,
        onUpdateDisplayName = { uid, name -> userNameVm?.onUpdateDisplayName(uid, name) },
        onRetryUsers = { userNameVm?.loadUsers() },
        usersLoadErrorMessage = userNameVm?.uiState?.loadErrorMessage,
        garbageLoading = garbageVm?.uiState?.isLoading ?: false,
        garbageLoadError = garbageVm?.uiState?.loadError ?: false,
        garbageLoadErrorMessage = garbageVm?.uiState?.loadErrorMessage,
        garbageSchedules = garbageVm?.uiState?.schedules ?: emptyList(),
        garbageMessage = garbageVm?.uiState?.message,
        garbageSaving = garbageVm?.uiState?.isSaving ?: false,
        onToggleDay = { type, day -> garbageVm?.onToggleDay(type, day) },
        onFrequencyChange = { type, freq -> garbageVm?.onChangeFrequency(type, freq) },
        onSaveGarbageSchedule = { garbageVm?.onSaveSchedule() },
        onRetryGarbageSchedule = { garbageVm?.loadSchedules() },
        garbageNotificationLoading = garbageVm?.uiState?.notificationLoading ?: false,
        garbageNotificationLoadError = garbageVm?.uiState?.notificationLoadError ?: false,
        garbageNotificationLoadErrorMessage = garbageVm?.uiState?.notificationLoadErrorMessage,
        garbageNotificationEnabled = garbageVm?.uiState?.notificationEnabled ?: false,
        garbageNotificationWebhookUrl = garbageVm?.uiState?.notificationWebhookUrl ?: "",
        garbageNotificationHour = garbageVm?.uiState?.notificationHour ?: "10",
        garbageNotificationPrefix = garbageVm?.uiState?.notificationPrefix ?: "",
        garbageNotificationSaving = garbageVm?.uiState?.notificationSaving ?: false,
        garbageNotificationHourValid = garbageVm?.uiState?.isNotificationHourValid ?: true,
        garbageNotificationMessage = garbageVm?.uiState?.notificationMessage,
        onGarbageNotificationEnabledChanged = { garbageVm?.onNotificationEnabledChanged(it) },
        onGarbageNotificationWebhookUrlChanged = { garbageVm?.onNotificationWebhookUrlChanged(it) },
        onGarbageNotificationHourChanged = { garbageVm?.onNotificationHourChanged(it) },
        onGarbageNotificationPrefixChanged = { garbageVm?.onNotificationPrefixChanged(it) },
        onSaveGarbageNotification = { garbageVm?.onSaveNotificationSettings() },
        onRetryGarbageNotification = { garbageVm?.loadNotificationSettings() },
        questWebhookLoading = questWebhookVm?.uiState?.isLoading ?: false,
        questWebhookLoadError = questWebhookVm?.uiState?.loadError ?: false,
        questWebhookLoadErrorMessage = questWebhookVm?.uiState?.loadErrorMessage,
        questWebhookUrl = questWebhookVm?.uiState?.url ?: "",
        questWebhookEnabled = questWebhookVm?.uiState?.enabled ?: false,
        questWebhookEvents = questWebhookVm?.uiState?.events ?: emptyList(),
        questWebhookSaving = questWebhookVm?.uiState?.isSaving ?: false,
        questWebhookMessage = questWebhookVm?.uiState?.message,
        onQuestWebhookUrlChanged = { questWebhookVm?.onUrlChanged(it) },
        onQuestWebhookEnabledChanged = { questWebhookVm?.onEnabledChanged(it) },
        onQuestWebhookToggleEvent = { questWebhookVm?.onToggleEvent(it) },
        onSaveQuestWebhook = { questWebhookVm?.onSave() },
        onRetryQuestWebhook = { questWebhookVm?.loadSettings() },
        cacheClearing = cacheVm?.uiState?.isClearing ?: false,
        cacheMessage = cacheVm?.uiState?.message,
        onClearCache = { cacheVm?.onClearCache() },
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun SettingsContent(
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
    usersLoading: Boolean = false,
    usersLoadError: Boolean = false,
    usersLoadErrorMessage: String? = null,
    users: List<User>,
    usersSaving: Boolean,
    usersMessage: String?,
    onUpdateDisplayName: (String, String) -> Unit,
    onRetryUsers: () -> Unit = {},
    garbageLoading: Boolean,
    garbageLoadError: Boolean = false,
    garbageLoadErrorMessage: String? = null,
    garbageSchedules: List<GarbageTypeSchedule>,
    garbageMessage: String?,
    garbageSaving: Boolean,
    onToggleDay: (GarbageType, Int) -> Unit,
    onFrequencyChange: (GarbageType, CollectionFrequency) -> Unit,
    onSaveGarbageSchedule: () -> Unit,
    onRetryGarbageSchedule: () -> Unit = {},
    garbageNotificationLoading: Boolean = false,
    garbageNotificationLoadError: Boolean = false,
    garbageNotificationLoadErrorMessage: String? = null,
    garbageNotificationEnabled: Boolean = false,
    garbageNotificationWebhookUrl: String = "",
    garbageNotificationHour: String = "10",
    garbageNotificationPrefix: String = "",
    garbageNotificationSaving: Boolean = false,
    garbageNotificationHourValid: Boolean = true,
    garbageNotificationMessage: String? = null,
    onGarbageNotificationEnabledChanged: (Boolean) -> Unit = {},
    onGarbageNotificationWebhookUrlChanged: (String) -> Unit = {},
    onGarbageNotificationHourChanged: (String) -> Unit = {},
    onGarbageNotificationPrefixChanged: (String) -> Unit = {},
    onSaveGarbageNotification: () -> Unit = {},
    onRetryGarbageNotification: () -> Unit = {},
    questWebhookLoading: Boolean = false,
    questWebhookLoadError: Boolean = false,
    questWebhookLoadErrorMessage: String? = null,
    questWebhookUrl: String = "",
    questWebhookEnabled: Boolean = false,
    questWebhookEvents: List<String> = emptyList(),
    questWebhookSaving: Boolean = false,
    questWebhookMessage: String? = null,
    onQuestWebhookUrlChanged: (String) -> Unit = {},
    onQuestWebhookEnabledChanged: (Boolean) -> Unit = {},
    onQuestWebhookToggleEvent: (String) -> Unit = {},
    onSaveQuestWebhook: () -> Unit = {},
    onRetryQuestWebhook: () -> Unit = {},
    cacheClearing: Boolean = false,
    cacheMessage: String? = null,
    onClearCache: () -> Unit = {},
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact
    val categories = SettingsCategory.entries.filter { !it.adminOnly || isAdmin }
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(if (isCompact) null else categories.firstOrNull()) }

    // Compact ↔ Expanded 切り替え時に selectedCategory を適切にリセット
    LaunchedEffect(isCompact) {
        if (isCompact) {
            selectedCategory = null
        } else if (selectedCategory == null) {
            selectedCategory = categories.firstOrNull()
        }
    }

    // isAdmin 変化等で categories から selectedCategory が除外された場合にリセット
    LaunchedEffect(categories) {
        if (selectedCategory != null && selectedCategory !in categories) {
            selectedCategory = if (isCompact) null else categories.firstOrNull()
        }
    }

    val categoryContent: @Composable (SettingsCategory, Modifier) -> Unit = { category, cardModifier ->
        when (category) {
            SettingsCategory.Account -> {
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
            SettingsCategory.UserManagement -> {
                UserNameManagementCard(
                    isLoading = usersLoading,
                    loadError = usersLoadError,
                    loadErrorMessage = usersLoadErrorMessage,
                    users = users,
                    usersSaving = usersSaving,
                    usersMessage = usersMessage,
                    onUpdateDisplayName = onUpdateDisplayName,
                    onRetry = onRetryUsers,
                    modifier = cardModifier,
                )
            }
            SettingsCategory.Garbage -> {
                GarbageScheduleCard(
                    isLoading = garbageLoading,
                    loadError = garbageLoadError,
                    loadErrorMessage = garbageLoadErrorMessage,
                    schedules = garbageSchedules,
                    garbageMessage = garbageMessage,
                    garbageSaving = garbageSaving,
                    onToggleDay = onToggleDay,
                    onFrequencyChange = onFrequencyChange,
                    onSaveClick = onSaveGarbageSchedule,
                    onRetry = onRetryGarbageSchedule,
                    modifier = cardModifier,
                )
                GarbageNotificationCard(
                    isLoading = garbageNotificationLoading,
                    loadError = garbageNotificationLoadError,
                    loadErrorMessage = garbageNotificationLoadErrorMessage,
                    enabled = garbageNotificationEnabled,
                    webhookUrl = garbageNotificationWebhookUrl,
                    notifyHour = garbageNotificationHour,
                    prefix = garbageNotificationPrefix,
                    isSaving = garbageNotificationSaving,
                    isHourValid = garbageNotificationHourValid,
                    message = garbageNotificationMessage,
                    onEnabledChanged = onGarbageNotificationEnabledChanged,
                    onWebhookUrlChanged = onGarbageNotificationWebhookUrlChanged,
                    onNotifyHourChanged = onGarbageNotificationHourChanged,
                    onPrefixChanged = onGarbageNotificationPrefixChanged,
                    onSave = onSaveGarbageNotification,
                    onRetry = onRetryGarbageNotification,
                    modifier = cardModifier,
                )
            }
            SettingsCategory.QuestWebhook -> {
                QuestWebhookSettingsCard(
                    isLoading = questWebhookLoading,
                    loadError = questWebhookLoadError,
                    loadErrorMessage = questWebhookLoadErrorMessage,
                    url = questWebhookUrl,
                    enabled = questWebhookEnabled,
                    events = questWebhookEvents,
                    isSaving = questWebhookSaving,
                    message = questWebhookMessage,
                    onUrlChanged = onQuestWebhookUrlChanged,
                    onEnabledChanged = onQuestWebhookEnabledChanged,
                    onToggleEvent = onQuestWebhookToggleEvent,
                    onSave = onSaveQuestWebhook,
                    onRetry = onRetryQuestWebhook,
                    modifier = cardModifier,
                )
            }
            SettingsCategory.Cache -> {
                CacheRefreshCard(
                    isClearing = cacheClearing,
                    message = cacheMessage,
                    onClearCache = onClearCache,
                    modifier = cardModifier,
                )
            }
        }
    }

    if (isCompact) {
        // Compact: カテゴリリスト ↔ カテゴリ詳細の切り替え
        val selected = selectedCategory
        if (selected == null) {
            CategoryListPane(
                categories = categories,
                selectedCategory = null,
                onSelectCategory = { selectedCategory = it },
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        } else {
            key(selected) {
                val detailScrollState = rememberScrollState()
                CategoryDetailPane(
                    category = selected,
                    scrollState = detailScrollState,
                    showBackButton = true,
                    onBack = { selectedCategory = null },
                    modifier = Modifier.fillMaxSize(),
                    contentModifier = Modifier.fillMaxWidth(),
                ) {
                    categoryContent(selected, Modifier.fillMaxWidth())
                }
            }
        }
    } else {
        // Medium / Expanded: 左カテゴリリスト + 右詳細の2ペイン
        Row(modifier = Modifier.fillMaxSize()) {
            CategoryListPane(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                modifier = Modifier.width(320.dp).fillMaxHeight().padding(24.dp),
            )

            VerticalDivider()

            val selected = selectedCategory ?: categories.firstOrNull()
            if (selected != null) {
                key(selected) {
                    val detailScrollState = rememberScrollState()
                    CategoryDetailPane(
                        category = selected,
                        scrollState = detailScrollState,
                        showBackButton = false,
                        onBack = {},
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentModifier = Modifier.widthIn(max = 480.dp),
                    ) {
                        categoryContent(selected, Modifier.widthIn(max = 480.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryListPane(
    categories: List<SettingsCategory>,
    selectedCategory: SettingsCategory?,
    onSelectCategory: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listScrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(listScrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEach { category ->
                CategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    onClick = { onSelectCategory(category) },
                )
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = settingsScrollbarStyle(),
        )
    }
}

@Composable
private fun CategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                if (category.adminOnly) {
                    AdminBadge()
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CategoryDetailPane(
    category: SettingsCategory,
    scrollState: ScrollState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ヘッダー（戻るボタン + タイトル）
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                }
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (category.adminOnly) {
                    AdminBadge()
                }
            }
            content()
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = settingsScrollbarStyle(),
        )
    }
}

@Composable
private fun UserNameManagementCard(
    isLoading: Boolean = false,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    users: List<User>,
    usersSaving: Boolean,
    usersMessage: String?,
    onUpdateDisplayName: (String, String) -> Unit,
    onRetry: () -> Unit = {},
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
        LoadableCardContent(
            isLoading = isLoading,
            loadError = loadError,
            loadErrorMessage = loadErrorMessage,
            onRetry = onRetry,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
    isLoading: Boolean,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    schedules: List<GarbageTypeSchedule>,
    garbageMessage: String?,
    garbageSaving: Boolean,
    onToggleDay: (GarbageType, Int) -> Unit,
    onFrequencyChange: (GarbageType, CollectionFrequency) -> Unit,
    onSaveClick: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        LoadableCardContent(
            isLoading = isLoading,
            loadError = loadError,
            loadErrorMessage = loadErrorMessage,
            onRetry = onRetry,
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
private fun QuestWebhookSettingsCard(
    isLoading: Boolean,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    url: String,
    enabled: Boolean,
    events: List<String>,
    isSaving: Boolean,
    message: String?,
    onUrlChanged: (String) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onToggleEvent: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        LoadableCardContent(
            isLoading = isLoading,
            loadError = loadError,
            loadErrorMessage = loadErrorMessage,
            onRetry = onRetry,
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
                    Text("クエスト Webhook 設定", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChanged,
                    )
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    label = { Text("クエスト Webhook URL") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                )

                Text("通知するイベント", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestWebhookEvent.all.forEach { event ->
                        FilterChip(
                            selected = event in events,
                            onClick = { onToggleEvent(event) },
                            label = { Text(QuestWebhookEvent.label(event)) },
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

@Composable
private fun GarbageNotificationCard(
    isLoading: Boolean,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    enabled: Boolean,
    webhookUrl: String,
    notifyHour: String,
    prefix: String,
    isSaving: Boolean,
    isHourValid: Boolean,
    message: String?,
    onEnabledChanged: (Boolean) -> Unit,
    onWebhookUrlChanged: (String) -> Unit,
    onNotifyHourChanged: (String) -> Unit,
    onPrefixChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        LoadableCardContent(
            isLoading = isLoading,
            loadError = loadError,
            loadErrorMessage = loadErrorMessage,
            onRetry = onRetry,
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
                        checked = enabled,
                        onCheckedChange = onEnabledChanged,
                    )
                }

                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = onWebhookUrlChanged,
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "通知時刻",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp),
                    )
                    OutlinedTextField(
                        value = notifyHour,
                        onValueChange = { v ->
                            val filtered = v.filter { it.isDigit() }.take(2)
                            onNotifyHourChanged(filtered)
                        },
                        label = { Text("時") },
                        singleLine = true,
                        isError = !isHourValid,
                        modifier = Modifier.width(72.dp),
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                    )
                    Text(": 00", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                    text = "この時刻に翌日のゴミ出し情報を通知します。ダッシュボードの表示切替は毎日 10:00 固定です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = onPrefixChanged,
                    label = { Text("通知テキスト") },
                    placeholder = { Text("@everyone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                )

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
                    enabled = !isSaving && isHourValid,
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
}
