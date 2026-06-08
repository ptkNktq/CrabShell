package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MoneyScreen(modifier: Modifier = Modifier) {
    val moneyVm: MoneyWebhookViewModel = koinViewModel()
    val paymentVm: PaymentWebhookViewModel = koinViewModel()
    val moneyState = moneyVm.uiState
    val paymentState = paymentVm.uiState

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WebhookSettingsCard(
            isLoading = moneyState.isLoading,
            title = "ステータス確定通知",
            featureEnabled = moneyState.enabled,
            url = moneyState.url,
            onUrlChanged = moneyVm::onUrlChanged,
            isSaving = moneyState.isSaving,
            onEnabledChanged = moneyVm::onEnabledChanged,
            onSave = moneyVm::onSave,
            modifier = Modifier.fillMaxWidth(),
            loadError = moneyState.loadError,
            loadErrorMessage = moneyState.loadErrorMessage,
            onRetry = moneyVm::loadSettings,
            description = "月のお金ステータスを「確定済み」に切り替えた際に Webhook で通知します。",
            message = moneyState.message,
            onMessageChanged = moneyVm::onMessageChanged,
            statusMessage = moneyState.statusMessage,
        )

        WebhookSettingsCard(
            isLoading = paymentState.isLoading,
            title = "入金通知",
            featureEnabled = paymentState.enabled,
            url = paymentState.url,
            onUrlChanged = paymentVm::onUrlChanged,
            isSaving = paymentState.isSaving,
            onEnabledChanged = paymentVm::onEnabledChanged,
            onSave = paymentVm::onSave,
            modifier = Modifier.fillMaxWidth(),
            loadError = paymentState.loadError,
            loadErrorMessage = paymentState.loadErrorMessage,
            onRetry = paymentVm::loadSettings,
            description = "ユーザーが入金を登録した際に Webhook で通知します。",
            message = paymentState.message,
            onMessageChanged = paymentVm::onMessageChanged,
            statusMessage = paymentState.statusMessage,
        )
    }
}
