package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.components.LoadableCardContent

/**
 * Webhook 系設定カードの共通レイアウト。
 *
 * 「タイトル + Switch → 説明 → Webhook URL → 通知テキスト → 独自パラメータ slot → ステータス → 保存ボタン」
 * という共通構造を提供する。各機能固有の入力（イベント選択・通知時刻・遅延・テストボタン等）は
 * URL・通知テキストの下に配置される [additionalContent] slot に差し込む。
 *
 * - [message] が null の場合は通知テキスト欄を表示しない（Quest のようにテキストを持たない設定向け）。
 * - [statusMessage] は保存結果などの一時表示用（入力欄ではない）。
 * - [fieldsEnabled] で URL/通知テキストの活性を制御する（給餌リマインダーのように
 *   有効トグルと連動させたい場合に `!isSaving && reminderEnabled` などを渡す）。
 * - [featureEnabled] は Switch の checked 状態（通知機能の有効/無効）を表す。
 *   Compose の `enabled`（操作可否）と区別するため別名にしている。
 * - Switch 自体の活性は常に `!isSaving`（保存中のみブロック）。
 */
@Composable
internal fun WebhookSettingsCard(
    isLoading: Boolean,
    title: String,
    featureEnabled: Boolean,
    url: String,
    onUrlChanged: (String) -> Unit,
    isSaving: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    onRetry: () -> Unit = {},
    description: String? = null,
    message: String? = null,
    onMessageChanged: (String) -> Unit = {},
    messageLabel: String = "通知テキスト",
    messagePlaceholder: String = "@everyone",
    fieldsEnabled: Boolean = !isSaving,
    statusMessage: String? = null,
    saveEnabled: Boolean = !isSaving,
    saveLabel: String = "保存する",
    additionalContent: @Composable ColumnScope.() -> Unit = {},
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
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = featureEnabled,
                        onCheckedChange = onEnabledChanged,
                        enabled = !isSaving,
                    )
                }

                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = fieldsEnabled,
                )

                if (message != null) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = onMessageChanged,
                        label = { Text(messageLabel) },
                        placeholder = { Text(messagePlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = fieldsEnabled,
                    )
                }

                additionalContent()

                if (statusMessage != null) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.height(48.dp),
                    enabled = saveEnabled,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(saveLabel)
                    }
                }
            }
        }
    }
}
