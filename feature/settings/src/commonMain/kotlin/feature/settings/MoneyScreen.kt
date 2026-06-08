package feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MoneyScreen(modifier: Modifier = Modifier) {
    val moneyVm: MoneyWebhookViewModel = koinViewModel()
    val paymentVm: PaymentWebhookViewModel = koinViewModel()
    val m = moneyVm.uiState
    val p = paymentVm.uiState

    WebhookSettingsCard(
        isLoading = m.isLoading,
        title = "ステータス確定通知",
        featureEnabled = m.enabled,
        url = m.url,
        onUrlChanged = moneyVm::onUrlChanged,
        isSaving = m.isSaving,
        onEnabledChanged = moneyVm::onEnabledChanged,
        onSave = moneyVm::onSave,
        modifier = modifier,
        loadError = m.loadError,
        loadErrorMessage = m.loadErrorMessage,
        onRetry = moneyVm::loadSettings,
        description = "月のお金ステータスを「確定済み」に切り替えた際に Webhook で通知します。",
        message = m.message,
        onMessageChanged = moneyVm::onMessageChanged,
        statusMessage = m.statusMessage,
    )

    WebhookSettingsCard(
        isLoading = p.isLoading,
        title = "入金通知",
        featureEnabled = p.enabled,
        url = p.url,
        onUrlChanged = paymentVm::onUrlChanged,
        isSaving = p.isSaving,
        onEnabledChanged = paymentVm::onEnabledChanged,
        onSave = paymentVm::onSave,
        modifier = modifier,
        loadError = p.loadError,
        loadErrorMessage = p.loadErrorMessage,
        onRetry = paymentVm::loadSettings,
        description = "ユーザーが入金を登録した際に Webhook で通知します。",
        message = p.message,
        onMessageChanged = paymentVm::onMessageChanged,
        statusMessage = p.statusMessage,
    )
}
