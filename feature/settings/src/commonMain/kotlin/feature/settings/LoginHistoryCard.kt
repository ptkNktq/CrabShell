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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.ui.components.LoadableCardContent
import core.ui.util.formatIsoToJst
import model.LoginEvent
import model.LoginMethod

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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatIsoToJst(event.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val method = event.loginMethod
            if (method != null) {
                LoginMethodBadge(method)
            }
        }

        val detail = buildDetailText(event.ipAddress, event.userAgent)
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoginMethodBadge(loginMethod: LoginMethod) {
    val (label, icon) =
        when (loginMethod) {
            LoginMethod.PASSKEY -> "パスキー" to Icons.Default.Fingerprint
            LoginMethod.EMAIL -> "メール" to Icons.Default.Email
        }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** IP とブラウザ名を "ip · browser" 形式で結合 */
private fun buildDetailText(
    ip: String?,
    ua: String?,
): String {
    val parts = listOfNotNull(ip, ua?.let { summarizeUserAgent(it) })
    return parts.joinToString(" · ")
}

/** UserAgent を簡略表示（ブラウザ名を抽出） */
private fun summarizeUserAgent(ua: String): String {
    // iOS 上の Chrome / Firefox は WKWebView ベースで UA に Safari/ が含まれるため、
    // CriOS / FxiOS を先に判定する。Android Chrome は "Android" + "Chrome/" で分岐。
    return when {
        "Edg/" in ua || "EdgiOS/" in ua || "EdgA/" in ua -> "Microsoft Edge"
        "OPR/" in ua || "Opera/" in ua || "OPiOS/" in ua -> "Opera"
        "CriOS/" in ua -> "Google Chrome (iOS)"
        "FxiOS/" in ua -> "Mozilla Firefox (iOS)"
        "Android" in ua && "Chrome/" in ua && "Safari/" in ua -> "Google Chrome (Android)"
        "Chrome/" in ua && "Safari/" in ua -> "Google Chrome"
        "Firefox/" in ua -> "Mozilla Firefox"
        "Safari/" in ua -> "Safari"
        else -> ua.take(50)
    }
}
