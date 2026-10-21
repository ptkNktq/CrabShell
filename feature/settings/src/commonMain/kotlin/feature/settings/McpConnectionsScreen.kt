package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.ui.components.AppButton
import core.ui.components.AppOutlinedButton
import core.ui.components.AppTextButton
import core.ui.components.LoadableCardContent
import core.ui.util.formatIsoToJst
import model.McpConnection
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun McpConnectionsScreen(modifier: Modifier = Modifier) {
    val vm: McpConnectionsViewModel = koinViewModel()

    McpConnectionsContent(
        state = vm.uiState,
        onRetry = vm::load,
        onRequestDisable = vm::onRequestDisable,
        onCancelDisable = vm::onCancelDisable,
        onConfirmDisable = vm::onConfirmDisable,
        onEnable = vm::onEnable,
        modifier = modifier,
    )
}

@Composable
internal fun McpConnectionsContent(
    state: McpConnectionsUiState,
    onRetry: () -> Unit,
    onRequestDisable: (String) -> Unit,
    onCancelDisable: () -> Unit,
    onConfirmDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        LoadableCardContent(
            isLoading = state.isLoading,
            loadError = state.loadError,
            loadErrorMessage = state.loadErrorMessage,
            onRetry = onRetry,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "AI アプリとの連携", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }

                if (!state.enabled) {
                    Text(
                        text = "AI アプリとの連携は有効になっていません。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }

                Text(
                    text =
                        "CrabShell を使った AI アプリの一覧です。無効にすると、そのアプリからの操作をすぐに止めます（あとで有効に戻せます）。" +
                            "同じアプリを複数の端末で使っている場合も 1 件にまとめて表示され、無効にするとすべての端末で使えなくなります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.connections.isEmpty()) {
                    Text(
                        text = "連携しているアプリはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.connections.forEachIndexed { index, connection ->
                            McpConnectionRow(
                                connection = connection,
                                isConfirmingDisable = state.confirmingDisableId == connection.id,
                                isUpdating = state.updatingId == connection.id,
                                // 実行中は他の行の操作も止め、結果の表示が入れ替わらないようにする
                                actionsEnabled = state.updatingId == null,
                                onRequestDisable = { onRequestDisable(connection.id) },
                                onCancelDisable = onCancelDisable,
                                onConfirmDisable = { onConfirmDisable(connection.id) },
                                onEnable = { onEnable(connection.id) },
                            )
                            if (index < state.connections.lastIndex) {
                                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                if (state.errorMessage != null) {
                    Text(text = state.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (state.successMessage != null) {
                    Text(text = state.successMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun McpConnectionRow(
    connection: McpConnection,
    isConfirmingDisable: Boolean,
    isUpdating: Boolean,
    actionsEnabled: Boolean,
    onRequestDisable: () -> Unit,
    onCancelDisable: () -> Unit,
    onConfirmDisable: () -> Unit,
    onEnable: () -> Unit,
) {
    val isDisabled = connection.disabledAt != null

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 日時は狭い画面でも折り返さないよう全幅に出し、ボタンはアプリ名の行に置く
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = connection.clientName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (isDisabled) DisabledBadge()
            }

            if (!isConfirmingDisable) {
                if (isDisabled) {
                    AppOutlinedButton(onClick = onEnable, enabled = actionsEnabled) {
                        if (isUpdating) {
                            // 実行中はボタンが無効になり中身も無効色になるため、スピナーの色は明示する
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text("有効にする")
                        }
                    }
                } else {
                    AppOutlinedButton(
                        onClick = onRequestDisable,
                        enabled = actionsEnabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("無効にする")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "初回利用: ${formatIsoToJst(connection.firstUsedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "最終利用: ${formatIsoToJst(connection.lastUsedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            connection.disabledAt?.let { disabledAt ->
                Text(
                    text = "無効化: ${formatIsoToJst(disabledAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (isConfirmingDisable) {
            DisableConfirmation(
                clientName = connection.clientName,
                isDisabling = isUpdating,
                onConfirm = onConfirmDisable,
                onCancel = onCancelDisable,
            )
        }
    }
}

/** 無効化の確認。Popup は使えないため、行の下にインラインで出す */
@Composable
private fun DisableConfirmation(
    clientName: String,
    isDisabling: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "「$clientName」を無効にしますか？このアプリからの操作は、すべての端末ですぐに使えなくなります。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(onClick = onCancel, enabled = !isDisabling) {
                    Text("キャンセル")
                }
                AppButton(
                    onClick = onConfirm,
                    enabled = !isDisabling,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    if (isDisabling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        Text("無効にする")
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = "無効",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
