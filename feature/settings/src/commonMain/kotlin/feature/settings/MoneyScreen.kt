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

    MoneyContent(
        moneyState = moneyVm.uiState,
        onMoneyUrlChanged = moneyVm::onUrlChanged,
        onMoneyEnabledChanged = moneyVm::onEnabledChanged,
        onMoneySave = moneyVm::onSave,
        onMoneyMessageChanged = moneyVm::onMessageChanged,
        onRetryMoney = moneyVm::loadSettings,
        paymentState = paymentVm.uiState,
        onPaymentUrlChanged = paymentVm::onUrlChanged,
        onPaymentEnabledChanged = paymentVm::onEnabledChanged,
        onPaymentSave = paymentVm::onSave,
        onPaymentMessageChanged = paymentVm::onMessageChanged,
        onRetryPayment = paymentVm::loadSettings,
        modifier = modifier,
    )
}

@Composable
internal fun MoneyContent(
    moneyState: MoneyWebhookUiState,
    onMoneyUrlChanged: (String) -> Unit,
    onMoneyEnabledChanged: (Boolean) -> Unit,
    onMoneySave: () -> Unit,
    onMoneyMessageChanged: (String) -> Unit,
    onRetryMoney: () -> Unit = {},
    paymentState: PaymentWebhookUiState,
    onPaymentUrlChanged: (String) -> Unit,
    onPaymentEnabledChanged: (Boolean) -> Unit,
    onPaymentSave: () -> Unit,
    onPaymentMessageChanged: (String) -> Unit,
    onRetryPayment: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WebhookSettingsCard(
            isLoading = moneyState.isLoading,
            title = "ステータス確定通知",
            featureEnabled = moneyState.enabled,
            url = moneyState.url,
            onUrlChanged = onMoneyUrlChanged,
            isSaving = moneyState.isSaving,
            onEnabledChanged = onMoneyEnabledChanged,
            onSave = onMoneySave,
            modifier = Modifier.fillMaxWidth(),
            loadError = moneyState.loadError,
            loadErrorMessage = moneyState.loadErrorMessage,
            onRetry = onRetryMoney,
            description = "月のお金ステータスを「確定済み」に切り替えた際に Webhook で通知します。",
            message = moneyState.message,
            onMessageChanged = onMoneyMessageChanged,
            statusMessage = moneyState.statusMessage,
        )

        WebhookSettingsCard(
            isLoading = paymentState.isLoading,
            title = "入金通知",
            featureEnabled = paymentState.enabled,
            url = paymentState.url,
            onUrlChanged = onPaymentUrlChanged,
            isSaving = paymentState.isSaving,
            onEnabledChanged = onPaymentEnabledChanged,
            onSave = onPaymentSave,
            modifier = Modifier.fillMaxWidth(),
            loadError = paymentState.loadError,
            loadErrorMessage = paymentState.loadErrorMessage,
            onRetry = onRetryPayment,
            description = "ユーザーが入金を登録した際に Webhook で通知します。",
            message = paymentState.message,
            onMessageChanged = onPaymentMessageChanged,
            statusMessage = paymentState.statusMessage,
        )
    }
}
