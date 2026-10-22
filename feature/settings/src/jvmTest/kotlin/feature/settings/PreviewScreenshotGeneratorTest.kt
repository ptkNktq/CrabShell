package feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import model.McpConnection
import model.PasskeyCredentialInfo
import kotlin.test.Test

/**
 * commonMain 化済みの settings 全8カテゴリ画面を JVM ターゲット上でオフスクリーンレンダリングし、
 * 3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 *
 * 各カテゴリの実際の Content コンポーネントに、各 ViewModel の UiState の
 * デフォルト値（isLoading = false のロード済み・空状態）を渡してレンダリングする。
 */
class PreviewScreenshotGeneratorTest {
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "settings")

    // 各カテゴリの実際の Content を、各 ViewModel の UiState のデフォルト値（ロード済み・空状態）で描画する。
    // koinViewModel() には触れないため Koin コンテキスト不要。
    @Composable
    private fun RealCategoryContent(
        category: SettingsCategory,
        modifier: Modifier,
    ) {
        when (category) {
            SettingsCategory.Account ->
                AccountContent(
                    passwordState = PasswordChangeUiState(),
                    onCurrentPasswordChanged = {},
                    onNewPasswordChanged = {},
                    onConfirmPasswordChanged = {},
                    onChangePassword = {},
                    passkeyState =
                        PasskeyManagementUiState(
                            isLoading = false,
                            isAvailable = true,
                            credentialCount = 1,
                            credentials =
                                listOf(
                                    PasskeyCredentialInfo(
                                        id = 1,
                                        createdAt = "2026-01-01T00:00:00Z",
                                        transports = listOf("internal"),
                                    ),
                                ),
                        ),
                    onRegisterPasskey = {},
                    onDeletePasskey = {},
                    loginHistoryState = LoginHistoryUiState(isLoading = false),
                    onRetryLoginHistory = {},
                    modifier = modifier,
                )
            SettingsCategory.Integrations ->
                McpConnectionsContent(
                    state = sampleMcpConnectionsState,
                    onRetry = {},
                    onRefresh = {},
                    onRequestDisable = {},
                    onCancelDisable = {},
                    onConfirmDisable = {},
                    onEnable = {},
                    modifier = modifier,
                )
            SettingsCategory.Credits ->
                CreditsContent(
                    state = LicensesUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.UserManagement ->
                UserManagementContent(
                    state = UserNameUiState(isLoading = false),
                    onUpdateDisplayName = { _, _ -> },
                    modifier = modifier,
                )
            SettingsCategory.Pet ->
                PetContent(
                    state = PetSettingsUiState(isLoading = false),
                    onPetNameChanged = { _, _ -> },
                    onSavePetName = {},
                    onMealOrderChanged = {},
                    onMealTimeChanged = { _, _ -> },
                    onSaveFeeding = {},
                    onReminderEnabledChanged = {},
                    onReminderWebhookUrlChanged = {},
                    onReminderDelayMinutesChanged = {},
                    onReminderPrefixChanged = {},
                    onSaveReminder = {},
                    onTestScheduled = {},
                    onTestReminder = {},
                    modifier = modifier,
                )
            SettingsCategory.Garbage ->
                GarbageContent(
                    state = GarbageScheduleUiState(isLoading = false, notificationLoading = false),
                    onToggleDay = { _, _ -> },
                    onFrequencyChange = { _, _ -> },
                    onSaveClick = {},
                    onNotificationEnabledChanged = {},
                    onNotificationWebhookUrlChanged = {},
                    onNotificationHourChanged = {},
                    onNotificationPrefixChanged = {},
                    onSaveNotificationSettings = {},
                    modifier = modifier,
                )
            SettingsCategory.Quest ->
                QuestContent(
                    state = QuestWebhookUiState(isLoading = false),
                    onUrlChanged = {},
                    onEnabledChanged = {},
                    onToggleEvent = {},
                    onSave = {},
                    modifier = modifier,
                )
            SettingsCategory.Money ->
                MoneyContent(
                    moneyState = MoneyWebhookUiState(isLoading = false),
                    onMoneyUrlChanged = {},
                    onMoneyEnabledChanged = {},
                    onMoneySave = {},
                    onMoneyMessageChanged = {},
                    paymentState = PaymentWebhookUiState(isLoading = false),
                    onPaymentUrlChanged = {},
                    onPaymentEnabledChanged = {},
                    onPaymentSave = {},
                    onPaymentMessageChanged = {},
                    dueDateState = MoneyDueDateNotificationUiState(isLoading = false),
                    onDueDateUrlChanged = {},
                    onDueDateEnabledChanged = {},
                    onDueDateSave = {},
                    onDueDatePrefixChanged = {},
                    onDueDateDaysBeforeChanged = {},
                    onDueDateNotifyHourChanged = {},
                    onDueDateTest = {},
                    modifier = modifier,
                )
            SettingsCategory.Cache ->
                CacheContent(
                    state = CacheRefreshUiState(),
                    onClearCache = {},
                    modifier = modifier,
                )
        }
    }

    private val sampleMcpConnectionsState =
        McpConnectionsUiState(
            isLoading = false,
            enabled = true,
            connections =
                listOf(
                    McpConnection(
                        id = "c1",
                        clientName = "Claude Code",
                        firstUsedAt = "2026-10-04T11:16:46Z",
                        lastUsedAt = "2026-10-21T00:03:04Z",
                    ),
                    McpConnection(
                        id = "c2",
                        clientName = "example.com",
                        firstUsedAt = "2026-10-10T03:00:00Z",
                        lastUsedAt = "2026-10-12T09:30:00Z",
                        disabledAt = "2026-10-15T12:00:00Z",
                    ),
                ),
        )

    @Test
    fun generateAllCategoryScreenshots() {
        standardSizePatterns.forEach { pattern ->
            SettingsCategory.entries.forEach { category ->
                recorder.save(
                    fileName = "settings_${category.name.lowercase()}_${pattern.label}.png",
                    width = pattern.width,
                    height = pattern.height,
                ) {
                    AppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            SettingsCategoryLayout(
                                isAdmin = true,
                                windowSizeClass = pattern.windowSizeClass,
                                selectedCategory = category,
                                categoryContent = { cat, mod -> RealCategoryContent(cat, mod) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 連携カテゴリは状態ごとの見た目も確認する（無効化の確認中・空・MCP 無効）
    @Test
    fun generateMcpConnectionsStateScreenshots() {
        val states =
            mapOf(
                "confirming" to sampleMcpConnectionsState.copy(confirmingDisableId = "c1"),
                "refreshing" to sampleMcpConnectionsState.copy(isRefreshing = true),
                "empty" to McpConnectionsUiState(isLoading = false, enabled = true),
                "disabled" to McpConnectionsUiState(isLoading = false, enabled = false),
            )
        standardSizePatterns.forEach { pattern ->
            states.forEach { (label, state) ->
                recorder.save(
                    fileName = "settings_integrations_${label}_${pattern.label}.png",
                    width = pattern.width,
                    height = pattern.height,
                ) {
                    AppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            SettingsCategoryLayout(
                                isAdmin = true,
                                windowSizeClass = pattern.windowSizeClass,
                                selectedCategory = SettingsCategory.Integrations,
                                categoryContent = { _, mod ->
                                    McpConnectionsContent(
                                        state = state,
                                        onRetry = {},
                                        onRefresh = {},
                                        onRequestDisable = {},
                                        onCancelDisable = {},
                                        onConfirmDisable = {},
                                        onEnable = {},
                                        modifier = mod,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
