package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.ui.components.LoadableCardContent
import model.LoginEvent

@Composable
fun LoginHistoryCard(
    viewModel: LoginHistoryViewModel,
    modifier: Modifier = Modifier,
) {
    LoginHistoryCardContent(
        isLoading = viewModel.uiState.isLoading,
        loadError = viewModel.uiState.loadError,
        loadErrorMessage = viewModel.uiState.loadErrorMessage,
        events = viewModel.uiState.events,
        onRetry = viewModel::loadHistory,
        modifier = modifier,
    )
}

@Composable
internal fun LoginHistoryCardContent(
    isLoading: Boolean,
    loadError: Boolean,
    loadErrorMessage: String?,
    events: List<LoginEvent>,
    onRetry: () -> Unit,
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "ログイン履歴",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (events.isEmpty()) {
                    Text(
                        text = "ログイン履歴はありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    events.forEachIndexed { index, event ->
                        LoginEventRow(event)
                        if (index < events.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginEventRow(event: LoginEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                when (event.loginMethod) {
                    "passkey" -> Icons.Default.Fingerprint
                    else -> Icons.Default.Email
                },
            contentDescription =
                when (event.loginMethod) {
                    "passkey" -> "パスキー"
                    else -> "メール"
                },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        when (event.loginMethod) {
                            "passkey" -> "パスキー"
                            "email" -> "メール"
                            else -> event.loginMethod ?: ""
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (event.ipAddress != null) {
                Text(
                    text = "IP: ${event.ipAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val ua = event.userAgent
            if (ua != null) {
                Text(
                    text = summarizeUserAgent(ua),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** ISO 8601 タイムスタンプを "yyyy/MM/dd HH:mm" 形式に簡易変換 */
private fun formatTimestamp(timestamp: String): String {
    if (timestamp.length < 16) return timestamp
    // "2026-04-16T23:50:00Z" -> "2026/04/16 23:50"
    return timestamp
        .take(16)
        .replace("-", "/")
        .replace("T", " ")
}

/** UserAgent を簡略表示（ブラウザ名を抽出） */
private fun summarizeUserAgent(ua: String): String {
    // 主要ブラウザを判定
    return when {
        "Edg/" in ua -> "Microsoft Edge"
        "Chrome/" in ua && "Safari/" in ua -> "Google Chrome"
        "Firefox/" in ua -> "Mozilla Firefox"
        "Safari/" in ua -> "Safari"
        else -> ua.take(50)
    }
}
