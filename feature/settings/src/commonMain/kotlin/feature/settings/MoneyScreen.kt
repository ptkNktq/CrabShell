package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.ui.components.AppOutlinedButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MoneyScreen(modifier: Modifier = Modifier) {
    val moneyVm: MoneyWebhookViewModel = koinViewModel()
    val paymentVm: PaymentWebhookViewModel = koinViewModel()
    val dueDateVm: MoneyDueDateNotificationViewModel = koinViewModel()

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
        dueDateState = dueDateVm.uiState,
        onDueDateUrlChanged = dueDateVm::onWebhookUrlChanged,
        onDueDateEnabledChanged = dueDateVm::onEnabledChanged,
        onDueDateSave = dueDateVm::onSave,
        onDueDatePrefixChanged = dueDateVm::onPrefixChanged,
        onDueDateDaysBeforeChanged = dueDateVm::onDaysBeforeChanged,
        onDueDateNotifyHourChanged = dueDateVm::onNotifyHourChanged,
        onDueDateTest = dueDateVm::onTest,
        onRetryDueDate = dueDateVm::loadSettings,
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
    dueDateState: MoneyDueDateNotificationUiState,
    onDueDateUrlChanged: (String) -> Unit,
    onDueDateEnabledChanged: (Boolean) -> Unit,
    onDueDateSave: () -> Unit,
    onDueDatePrefixChanged: (String) -> Unit,
    onDueDateDaysBeforeChanged: (String) -> Unit,
    onDueDateNotifyHourChanged: (String) -> Unit,
    onDueDateTest: () -> Unit,
    onRetryDueDate: () -> Unit = {},
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

        WebhookSettingsCard(
            isLoading = dueDateState.isLoading,
            title = "支払期日リマインダー",
            featureEnabled = dueDateState.enabled,
            url = dueDateState.webhookUrl,
            onUrlChanged = onDueDateUrlChanged,
            isSaving = dueDateState.isSaving,
            onEnabledChanged = onDueDateEnabledChanged,
            onSave = onDueDateSave,
            modifier = Modifier.fillMaxWidth(),
            loadError = dueDateState.loadError,
            loadErrorMessage = dueDateState.loadErrorMessage,
            onRetry = onRetryDueDate,
            description = "支払期日が設定された項目を、期日の指定日数前・指定時刻に Webhook で通知します。",
            message = dueDateState.prefix,
            onMessageChanged = onDueDatePrefixChanged,
            messagePlaceholder = "",
            statusMessage = dueDateState.statusMessage,
            saveEnabled =
                !dueDateState.isSaving && !dueDateState.isTesting && dueDateState.isDaysBeforeValid && dueDateState.isNotifyHourValid,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "通知タイミング", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                Text(text = "期日の", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = dueDateState.daysBefore,
                    onValueChange = onDueDateDaysBeforeChanged,
                    label = { Text("日前") },
                    singleLine = true,
                    isError = !dueDateState.isDaysBeforeValid,
                    modifier = Modifier.width(72.dp),
                    enabled = !dueDateState.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                )
                Text(text = "の", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = dueDateState.notifyHour,
                    onValueChange = onDueDateNotifyHourChanged,
                    label = { Text("時") },
                    singleLine = true,
                    isError = !dueDateState.isNotifyHourValid,
                    modifier = Modifier.width(72.dp),
                    enabled = !dueDateState.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                )
                Text(": 00", style = MaterialTheme.typography.titleMedium)
            }
            AppOutlinedButton(
                onClick = onDueDateTest,
                modifier = Modifier.height(48.dp),
                enabled =
                    !dueDateState.isSaving &&
                        !dueDateState.isTesting &&
                        dueDateState.enabled &&
                        dueDateState.webhookUrl.isNotBlank(),
            ) {
                if (dueDateState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("テスト送信")
                }
            }
        }
    }
}
